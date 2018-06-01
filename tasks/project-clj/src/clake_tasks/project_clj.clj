(ns clake-tasks.project-clj
  (:require
    [clojure.pprint :as pp]
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clake-tasks.util :as util])
  (:import (java.io File)))

(defn generate-project-clj-string
  [project-name lein-tools-deps-version]
  (with-out-str
    (pp/pprint
      (list
        'defproject (symbol project-name) "0.1.0"
        :description "FIXME: write description"
        :url "http://example.com/FIXME"
        :license {:name "Eclipse Public License"
                  :url  "http://www.eclipse.org/legal/epl-v10.html"}
        :plugins [['lein-tools-deps lein-tools-deps-version]]
        :lein-tools-deps/config {:config-files [:install :user :project]}))))

(defn project-clj
  [_]
  (let [cwd (System/getProperty "user.dir")
        parent-dir-name (last (str/split cwd (re-pattern File/separator)))
        project-string (generate-project-clj-string
                         parent-dir-name
                         "0.4.0-SNAPSHOT")]
    (spit (io/file cwd "project.clj") project-string)))