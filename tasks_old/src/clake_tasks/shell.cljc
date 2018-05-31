(ns clake-tasks.shell
  (:require
    [clojure.string :as str]
    #?(:clj
    [clojure.edn :as edn] :cljs [cljs.tools.reader.edn :as edn])
    #?(:clj
    [clojure.java.shell :as proc] :cljs ["child_process" :as proc])
    #?(:cljs ["tmp" :as tmp]))
  #?(:clj
     (:import (java.nio.file Files)
              (java.nio.file.attribute FileAttribute))))

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
                           (merge (when isolated? {:dir (create-tempdir)}) cmd-opts))]
    (cond-> r
            (and (status-success? r) (= as :edn))
            (update :out edn/read-string))))