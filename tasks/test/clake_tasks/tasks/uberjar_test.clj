(ns clake-tasks.tasks.uberjar-test
  (:require
    [clojure.test :refer :all]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.string :as str]
    [hara.io.file :as fs]
    [hara.io.archive :as archive]
    [clake-tasks.tasks.uberjar :as uberjar])
  (:import (java.nio.file Path)))

(def ^:dynamic *test-dir* nil)

(defmacro with-fileset
  [bindings & forms]
  (let [bindings (partition 2 bindings)]
    `(let [~@(mapcat (fn [[sym-name [path content]]]
                       [sym-name `(let [in-test-dir?# (str/starts-with? ~path (or (str *test-dir*) ""))
                                        out-path# (if in-test-dir?#
                                                    ~path
                                                    (fs/path *test-dir* ~path))
                                        parent-path# (fs/parent out-path#)]
                                    (when-not (fs/exists? parent-path#)
                                      (fs/create-directory parent-path#))
                                    (when ~content
                                      (fs/write-all-bytes out-path# (.getBytes (str ~content))))
                                    out-path#)]) bindings)]
       (try
         ~@forms
         (finally
           (doseq [path# ~(mapv (fn [[_ [path]]] path) bindings)]
             (when (fs/exists? path#)
               #_(fs/delete path#))))))))

(defn list-relative-files
  [path]
  (mapv (partial fs/relativize path)
        (keys (fs/list path {:exclude   [fs/directory?]
                             :recursive true}))))

(comment
  (with-fileset [data-readers ["data_readers.clj" {:a "a"}]]
    )
  )

(defn temp-dir-fixture
  [f]
  (let [tdir (fs/create-tmpdir)]
    (binding [*test-dir* tdir]
      (f)
      #_(fs/delete tdir))))

;; support slurp'ing Path objects
(extend Path
  io/IOFactory
  (assoc io/default-streams-impl
    :make-input-stream (fn [path opts] (fs/input-stream path))
    :make-output-stream (fn [path opts] (fs/output-stream path))))

(use-fixtures :each temp-dir-fixture)

(deftest merge-edn-files-test
  []
  (with-fileset [a-edn ["a.edn" {:a "a"}]
                 b-edn ["b.edn" {:b "b"}]]
    (uberjar/merge-edn-files a-edn a-edn b-edn)
    (is (= {:a "a"
            :b "b"}
           (edn/read-string (slurp a-edn))))))

(deftest file-name-test
  (with-fileset [a-edn ["a.edn" ""]]
    (is (= "a.edn" (uberjar/file-name a-edn)))))

(deftest trim-beginning-slash-test
  (is (= "a" (uberjar/trim-beginning-slash "a")))
  (is (= "a" (uberjar/trim-beginning-slash "/a"))))

(deftest copy-source-test
  (testing "copy directory"
    (with-fileset [directory-source ["src"]
                   _ [(str directory-source "/foo/bar/core.clj") '(ns foo.bar.core)]
                   directory-target ["directory-target"]]
      (uberjar/copy-source directory-source directory-target)
      (is (= (list-relative-files directory-source)
             (list-relative-files directory-target)))))
  (testing "copy jar"
    (with-fileset [jar-contents-dir ["contents"]
                   _ [(str jar-contents-dir "/foo/bar/core2.clj") '(ns foo.bar.core2)]
                   jar-path ["myjar.jar"]
                   target ["jar-target"]]
      (archive/archive jar-path jar-contents-dir)
      (uberjar/copy-source jar-path target)
      (is (= (list-relative-files target)
             (list-relative-files jar-contents-dir))))))

(deftest move-source-files-test
  (with-fileset [source-dir ["source"]
                 target-dir ["target"]
                 project1 [(str source-dir "/project1")]
                 project2 [(str source-dir "/project2")]
                 _ [(str project1 "/data_readers.clj") '{a a}]
                 _ [(str project2 "/data_readers.clj") '{b b}]]
    (let [all-files (into #{} (concat (list-relative-files project1)
                                      (list-relative-files project2)))]
      (uberjar/move-source-files project1 target-dir)
      (uberjar/move-source-files project2 target-dir)
      (testing "Ensure data_readers.clj is merged"
        (is (= '{a a b b}
               (edn/read-string (slurp (fs/path target-dir "data_readers.clj"))))))
      (testing "Ensure all files all moved"
        (is (= all-files
               (into #{} (list-relative-files target-dir))))))))

(deftest explode-classpath-test
  (with-fileset [cp-directory ["src"]
                 _ ["src/project/core.clj" '(ns project.core)]
                 _ ["src/data_readers.clj" '{project project}]
                 cp-jar [".m2/repository/mylib.jar"]
                 cp-jar-contents ["jar"]
                 _ ["jar/foo/bar/core.clj" '(ns foo.bar.core)]
                 _ ["jar/data_readers.clj" '{jar jar}]
                 target ["target"]]
    (archive/archive cp-jar cp-jar-contents)
    (uberjar/explode-classpath [cp-directory cp-jar] target)
    (let [all-files (into #{} (concat (list-relative-files cp-directory)
                                      (list-relative-files cp-jar-contents)))
          target-files (into #{} (list-relative-files target))]
      (is (= all-files target-files)))))