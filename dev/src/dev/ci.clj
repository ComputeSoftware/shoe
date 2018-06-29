(ns dev.ci
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.pprint :as pp]
    [clojure.edn :as edn])
  (:import (java.io FilenameFilter)))

(defn find-shoe-deps
  [deps-edn]
  (into #{}
        (filter (fn [artifact-name]
                  (contains? #{"shoe" "shoe.tasks"} (namespace artifact-name))))
        (keys (:deps deps-edn))))

(defn replace-shoe-sha
  [deps-edn sha]
  (let [paths (map (partial vector :deps) (find-shoe-deps deps-edn))]
    (reduce (fn [deps-edn dep-path]
              (update-in deps-edn dep-path
                         (fn [coord]
                           (if (:sha coord)
                             (assoc coord :sha sha)
                             coord))))
            deps-edn paths)))

;; TODO: replace with better formatter
;; zprint, clj-fmt
(defn format-deps-edn
  [deps-edn]
  (str/replace (binding [*print-namespace-maps* false]
                 (with-out-str (pp/pprint deps-edn)))
               "," ""))

(defn task-deps-edn-paths
  [root-path]
  (let [tasks-parent (io/file root-path "tasks")]
    (mapv
      (fn [task-root]
        (.getAbsolutePath (io/file tasks-parent task-root "deps.edn")))
      (.list tasks-parent
             (proxy [FilenameFilter] []
               (accept [current name]
                 (and (.isDirectory (io/file current name))
                      (not (str/starts-with? name "."))
                      (.exists (io/file current name "deps.edn")))))))))

(defn replace-git-sha
  {:shoe/cli-specs [["-s" "--sha VAL" "The SHA to replace in all the tasks."]]}
  [{:keys [sha]}]
  (assert sha ":sha not set.")
  (doseq [p (task-deps-edn-paths "..")]
    (let [deps-edn (edn/read-string (slurp p))
          new-deps-edn (replace-shoe-sha deps-edn sha)]
      (spit p (format-deps-edn new-deps-edn)))))

(comment
  (def deps-edn
    '{:deps {org.clojure/clojure      {:mvn/version "1.9.0"}
             zcaudate/hara.io.file    {:mvn/version "2.8.2"}
             zcaudate/hara.io.archive {:mvn/version "2.8.2"}
             shoe/common              {:git/url   "https://github.com/ComputeSoftware/shoe"
                                       :deps/root "common"
                                       :sha       "0e5301e7627f87c7e134816a8fd6fe186f88a90a"}
             shoe.tasks/uberjar       {:git/url   "https://github.com/ComputeSoftware/shoe"
                                       :deps/root "common"
                                       :sha       "0e5301e7627f87c7e134816a8fd6fe186f88a90a"}}})
  )