(ns clake-tasks.test-util
  (:require
    [clojure.test :refer :all]
    [clojure.string :as str]
    [hara.io.file :as fs]))

(def ^:dynamic *test-dir* nil)

(defn temp-dir-fixture
  [f]
  (let [tdir (fs/create-tmpdir)]
    (binding [*test-dir* tdir]
      (f)
      (fs/delete tdir))))

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
           ;; TODO: this is super broken. do not use
           #_(doseq [path# ~(mapv (fn [[_ [path]]] path) bindings)]
             (when (fs/exists? path#)
               (fs/delete path#))))))))

(comment
  (with-fileset [data-readers ["data_readers.clj" {:a "a"}]]
    )
  )