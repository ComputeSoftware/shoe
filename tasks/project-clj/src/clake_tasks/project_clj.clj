(ns clake-tasks.project-clj
  (:require
    [clojure.pprint :as pp]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import (java.io File)))

(def cwd (System/getProperty "user.dir"))

(defn lein-tools-deps-project-clj-params
  [lein-tools-deps-version]
  {:plugins                [['lein-tools-deps lein-tools-deps-version]]
   :middleware             ['lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
   :lein-tools-deps/config {:config-files [:install :user :project]}})

(defn generate-project-clj-string
  [project-name lein-tools-deps-version]
  (with-out-str
    (pp/pprint
      (list
        'defproject (symbol project-name) "0.1.0"
        :plugins [['lein-tools-deps lein-tools-deps-version]]
        :middleware ['lein-tools-deps.plugin/resolve-dependencies-with-deps-edn]
        :lein-tools-deps/config {:config-files [:install :user :project]}))))

(defn project-clj-exists?
  []
  (.exists (io/file cwd "project.clj")))

(defn project-name-from-parent-dir
  []
  (last (str/split cwd (re-pattern File/separator))))

(defn project-clj
  [_]
  (let [parent-dir-name (project-name-from-parent-dir)
        project-string (generate-project-clj-string
                         parent-dir-name
                         "0.4.0-SNAPSHOT")]
    (if (project-clj-exists?)
      )
    (spit (io/file cwd "project.clj") project-string)))

(defn -main
  [& args]
  (project-clj nil))