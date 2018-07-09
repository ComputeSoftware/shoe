(ns shoe-common.shell
  (:require
    [clojure.string :as str]
    #?(:clj
    [clojure.java.io :as io])
    #?(:clj
    [clojure.edn :as edn] :cljs [cljs.tools.reader.edn :as edn])
    #?(:cljs ["child_process" :as proc])
    [shoe-common.log :as log])
  #?(:clj
     (:import (java.nio.file Files)
              (java.nio.file.attribute FileAttribute)
              (java.lang ProcessBuilder$Redirect))))

(defn exit?
  "Returns true if `x` is an exit map."
  [x]
  (some? (:shoe-exit/status x)))

(defn exit
  "Return a map that can be passed to `system-exit` to exit the process."
  ([ok?-or-status] (exit ok?-or-status nil))
  ([ok?-or-status msg]
   (let [status (if (number? ok?-or-status)
                  ok?-or-status
                  (if ok?-or-status 0 1))
         ok? (= status 0)
         exit-map (cond-> {:shoe-exit/status status
                           :shoe-exit/ok?    ok?}
                    msg (assoc :shoe-exit/message msg))]
     exit-map)))

(defn system-exit
  "Exit the process."
  [{:shoe-exit/keys [status message]}]
  (when (and message (not (str/blank? message)))
    (if (= status 0)
      (log/info message)
      (log/error message)))
  #?(:clj (System/exit status) :cljs (.exit js/process status)))

;(defn create-tempdir
;  []
;  #?(:clj  (str (Files/createTempDirectory "" (make-array FileAttribute 0)))
;     :cljs (.-name (tmp/dirSync))))

(defn status-success?
  [r]
  (= 0 (:exit r)))

(defn status-failed?
  [r]
  (not (status-success? r)))

(defn classpath-error?
  [r]
  (some? (re-find #"Error building classpath\." (or (:err r) ""))))

#?(:clj
   ;; we need to write out own shell function instead of Clojure's because of
   ;; https://dev.clojure.org/jira/browse/CLJ-959
   (defn spawn-sync-jvm
     ([args] (spawn-sync-jvm args nil))
     ([args {:keys [stdio dir]}]
      (let [[stdin-opt stdout-opt stderr-opt] (let [[stdin stdout stderr] stdio]
                                                [(or stdin "pipe")
                                                 (or stdout "pipe")
                                                 (or stderr "pipe")])
            builder (ProcessBuilder. ^"[Ljava.lang.String;" (into-array String args))]
        ;; configure builder
        (case stdin-opt
          "pipe" (.redirectInput builder ProcessBuilder$Redirect/PIPE)
          "inherit" (.redirectInput builder ProcessBuilder$Redirect/INHERIT))
        (case stdout-opt
          "pipe" (.redirectOutput builder ProcessBuilder$Redirect/PIPE)
          "inherit" (.redirectOutput builder ProcessBuilder$Redirect/INHERIT))
        (case stderr-opt
          "pipe" (.redirectError builder ProcessBuilder$Redirect/PIPE)
          "inherit" (.redirectError builder ProcessBuilder$Redirect/INHERIT))
        (when dir
          (.directory builder (io/file dir)))
        (let [proc (.start builder)]
          (with-open [stdout (.getInputStream proc)
                      stderr (.getErrorStream proc)]
            (let [status (.waitFor proc)]
              {:exit status
               :out  (slurp stdout)
               :err  (slurp stderr)})))))))

(defn spawn-sync
  ([command] (spawn-sync command nil))
  ([command args] (spawn-sync command args nil))
  ([command args {:keys [dir] :as opts}]
   (let [args (filter some? args)]
     #?(:clj  (spawn-sync-jvm (concat [command] (map str args)) opts)
        :cljs (let [result (proc/spawnSync command
                                           (to-array args)
                                           (clj->js (cond-> {}
                                                            dir (assoc :cwd dir)
                                                            true (merge opts))))]
                {:exit (.-status result)
                 :out  (when-let [s (.-stdout result)]
                         (.toString s))
                 :err  (when-let [s (.-stderr result)]
                         (.toString s))})))))

(defn clojure-command
  ([args] (clojure-command args nil))
  ([args opts]
   (spawn-sync "clojure" args opts)))

(defn clojure-deps-command
  [{:keys [deps-edn aliases isolated? command eval-code main args as cmd-opts]}]
  (assert (or (nil? command) (contains? #{:path :pom :describe} command)))
  (assert (or (nil? eval-code) (vector? eval-code)))
  (let [r (clojure-command (vec (concat
                                  (when deps-edn
                                    ["-Sdeps" deps-edn])
                                  [(when-not (empty? aliases)
                                     (str "-A" (str/join aliases)))
                                   (when command
                                     (str "-S" (name command)))]
                                  (when eval-code
                                    ["-e" (str/join " " eval-code)])
                                  (when main
                                    ["-m" (str main) (when args args)])))
                           (merge (when isolated? #_{:dir (create-tempdir)}) cmd-opts))]
    (cond-> r
            (and (status-success? r) (= as :edn))
            (update :out edn/read-string))))

(defn deps-config-files
  "Returns a vector of tools-deps config files."
  []
  (-> (clojure-deps-command {:command :describe
                             :as      :edn})
      :out :config-files))

(defn full-deps-edn
  "Returns the fully merged deps.edn as EDN."
  [deps-edn-paths]
  (let [deps {:deps {'org.clojure/tools.deps.alpha {:mvn/version "0.5.435"}}}
        code ['(require '[clojure.tools.deps.alpha.reader :as reader])
              (list 'reader/read-deps deps-edn-paths)]]
    (:out (clojure-deps-command {:deps-edn  deps
                                 :eval-code code
                                 :as        :edn}))))

(defn classpath-string-from-clj
  []
  ;; TODO: include aliases here
  (let [r (clojure-deps-command {:command :path})]
    (when (status-success? r)
      (str/trim-newline (:out r)))))