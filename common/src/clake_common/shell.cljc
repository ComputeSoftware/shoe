(ns clake-common.shell
  (:require
    [clojure.string :as str]
    #?(:clj
    [clojure.edn :as edn] :cljs [cljs.tools.reader.edn :as edn])
    #?(:clj
    [clojure.java.shell :as proc] :cljs ["child_process" :as proc])
    [clake-common.log :as log])
  #?(:clj
     (:import (java.nio.file Files)
              (java.nio.file.attribute FileAttribute))))

(defn exit?
  "Returns true if `x` is an exit map."
  [x]
  (some? (:clake-exit/status x)))

(defn system-exit
  "Exit the process."
  [{:clake-exit/keys [status message]}]
  (when message
    (if (= status 0)
      (log/info message)
      (log/error message)))
  #?(:clj (System/exit status) :cljs (.exit js/process status)))

(defn exit
  "Return a map that can be passed to `system-exit` to exit the process."
  ([ok?-or-status] (exit ok?-or-status nil))
  ([ok?-or-status msg]
   (let [status (if (number? ok?-or-status)
                  ok?-or-status
                  (if ok?-or-status 0 1))
         ok? (= status 0)]
     (cond-> {:clake-exit/status status
              :clake-exit/ok?    ok?}
             msg (assoc :clake-exit/message msg)))))

(defn create-tempdir
  []
  #?(:clj  (str (Files/createTempDirectory "" (make-array FileAttribute 0)))
     :cljs (.-name (tmp/dirSync))))

(defn status-success?
  [r]
  (= 0 (:exit r)))

(defn status-failed?
  [r]
  (not (status-success? r)))

(defn spawn-sync
  ([command] (spawn-sync command nil))
  ([command args] (spawn-sync command args nil))
  ([command args {:keys [dir] :as opts}]
   (let [args (filter some? args)]
     #?(:clj  (apply proc/sh (concat [command]
                                     (map str args)
                                     (mapcat identity {:dir dir})))
        :cljs (let [result (proc/spawnSync command
                                           (to-array args)
                                           (clj->js (cond-> {}
                                                            dir (assoc :cwd dir)
                                                            true (merge opts))))]
                {:exit (.-status result)
                 :out  (.toString (.-stdout result))
                 :err  (.toString (.-stderr result))})))))

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