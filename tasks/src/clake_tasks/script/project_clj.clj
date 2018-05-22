(ns clake-tasks.script.project-clj
  (:require
    [clojure.pprint :as pp]
    [hara.io.file :as fs]
    [clake-tasks.util :as util]))

(defn generate-project-clj-string
  [project-name lein-tools-deps-version deps-edn-path]
  (with-out-str
    (pp/pprint
      (list
        'defproject (symbol project-name) "0.1.0"
        :description "FIXME: write description"
        :url "http://example.com/FIXME"
        :license {:name "Eclipse Public License"
                  :url  "http://www.eclipse.org/legal/epl-v10.html"}
        :plugins [['lein-tools-deps lein-tools-deps-version]]
        :tools/deps [:system :home deps-edn-path]))))

(defn project-clj
  [opts ctx]
  (let [cwd (System/getProperty "user.dir")
        parent-dir-name (util/file-name cwd)
        project-string (generate-project-clj-string
                         parent-dir-name
                         "0.3.0-SNAPSHOT"
                         (str (fs/path cwd "deps.edn")))]
    (spit (str (fs/path cwd "project.clj")) project-string)))