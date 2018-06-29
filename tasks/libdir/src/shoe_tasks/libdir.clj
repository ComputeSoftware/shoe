(ns shoe-tasks.libdir
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [clojure.java.io :as io]
    [hara.io.file :as fs]
    [hara.io.archive :as archive]
    [shoe-common.log :as log]
    [shoe-common.shell :as shell]
    [shoe-common.util :as util]))

(defn trim-beginning-slash
  [s]
  (if (str/starts-with? s "/")
    (subs s 1)
    s))

(defn file-name
  [path]
  (str (fs/relativize (fs/parent (fs/path path)) (fs/path path))))

(defn excluded?
  "Returns true of `filename` should be excluded from the JAR."
  [filename]
  (let [filename (trim-beginning-slash (str filename))]
    (or (contains? #{"project.clj"
                     "LICENSE"
                     "COPYRIGHT"} filename)
        (re-matches #"(?i)META-INF/.*\.(?:MF|SF|RSA|DSA)" filename)
        (re-matches #"(?i)META-INF/(?:INDEX\.LIST|DEPENDENCIES|NOTICE|LICENSE)(?:\.txt)?" filename))))

(defmulti copy-source (fn [src dest]
                        (cond
                          (fs/directory? src) :directory
                          (and (fs/file? src)
                               (re-find #"\.jar$" (str src))) :jar
                          :else :default)))

(defmethod copy-source :default
  [src _]
  (log/error (str "Could not copy classpath entry: " src)))

(defmethod copy-source :directory
  [src dest]
  (let [all-files (keys (fs/list src {:recursive true
                                      :exclude   [fs/directory?]}))
        source-dest (map (fn [source-path]
                           {:source source-path
                            :dest   (fs/path dest (fs/relativize src source-path))})
                         all-files)]
    (doseq [{:keys [source dest]} source-dest]
      (fs/copy source dest {:exclude [excluded?]}))))

(defmethod copy-source :jar
  [src dest]
  (let [entries-to-extract (filter (fn [jar-path]
                                     ;; remove the starting / from the jar paths
                                     (and (not (fs/directory? jar-path))
                                          (-> jar-path str (subs 1) (excluded?) (not))))
                                   (archive/list src))]
    ;; for some reason the extract function return value needs to be evaluated in
    ;; in order for the operation to execute. yuck.
    (doall (archive/extract src dest entries-to-extract))))

(defn merge-edn-files
  [target-path f1 f2]
  (with-open [f1-reader (fs/reader f1)
              f2-reader (fs/reader f2)]
    (spit target-path
          (merge (edn/read-string (slurp f1-reader))
                 (edn/read-string (slurp f2-reader))))))

(defn merge-files
  [existing-path new-path]
  (cond
    (= "data_readers.clj" (file-name existing-path))
    (merge-edn-files existing-path existing-path new-path)
    ;(re-find #"^META-INF/services/" (trim-beginning-slash new-path))
    :else (fs/move new-path existing-path)))

(defn move-source-files
  [source-dir target-dir]
  (let [files (keys (fs/list source-dir {:recursive true
                                         :exclude   [fs/directory?]}))]
    (doseq [path files]
      (let [target-path (fs/path target-dir (fs/relativize source-dir path))]
        (if (fs/exists? target-path)
          (merge-files target-path path)
          (fs/move path target-path))))))

(defn explode-classpath
  "Creates a directory containing all files on the classpath. `classpath-vec is
  a vector of classpath locations. `target` is the location to explode "
  [classpath-vec target]
  (let [temp-dirs (reduce (fn [acc src]
                            (conj acc {:source src
                                       :dest   (fs/create-tmpdir)}))
                          [] classpath-vec)]
    ;; copy all source files to temporary directories
    (doseq [{:keys [source dest]} temp-dirs]
      (copy-source source dest))
    ;; move files one by one into the target directory, merging when applicable
    (doseq [{:keys [dest]} temp-dirs]
      (move-source-files dest target))))

(defn libdir
  {:shoe/cli-specs [["-o" "--out PATH" "Path to output the libs to."
                      :validate-fn (fn [path]
                                     (.exists (io/file path)))
                      :default "libs"]]}
  [{:keys [out]}]
  (let [cp-vec (util/parse-classpath-string (shell/classpath-string-from-clj))]
    (explode-classpath cp-vec out)))