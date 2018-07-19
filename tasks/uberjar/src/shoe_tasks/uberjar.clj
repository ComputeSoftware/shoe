(ns shoe-tasks.uberjar
  (:require
    [clojure.edn :as edn]
    [clojure.string :as str]
    [shoe-common.fs :as fs]
    [shoe-common.task :as task]
    [shoe-common.cli-utils :as cli-utils]
    [shoe-common.log :as log]
    [shoe-common.util :as util]
    [shoe-tasks.jar :as jar-task]
    [shoe-tasks.libdir :as libdir-task]
    [shoe-tasks.aot :as aot-task]))

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

(defn merge-edn-files
  [target-path f1 f2]
  (spit target-path
        (merge (edn/read-string (slurp f1))
               (edn/read-string (slurp f2)))))

(defn merge-files
  [existing-path new-path]
  (cond
    (= "data_readers.clj" (file-name existing-path))
    (merge-edn-files existing-path existing-path new-path)
    ;(re-find #"^META-INF/services/" (trim-beginning-slash new-path))
    :else (fs/move new-path existing-path {:overwrite? true})))

(defn move-source-files
  [source-dir target-dir]
  (let [source-files (fs/list source-dir {:filter-fn fs/file?})]
    (doseq [source-path source-files]
      (let [target-path (fs/path target-dir (fs/relativize source-dir source-path))]
        (if (fs/exists? target-path)
          (merge-files target-path source-path)
          (fs/move source-path target-path))))))

(defn extract-jar
  [jar-path target-dir]
  (fs/extract-archive
    jar-path target-dir
    {:filter-fn
     (fn [jar-path]
       ;; remove the starting / from the jar paths
       (and (fs/file? jar-path)
            (-> jar-path str (subs 1) (excluded?) (not))))}))

(defn explode-jars-to-temp-dirs
  [jar-paths]
  (let [jar-to-tempdir (map #(vector % (fs/create-temp-directory "shoe")) jar-paths)]
    (doseq [[jar-path target-dir] jar-to-tempdir]
      (extract-jar jar-path target-dir))
    (map second jar-to-tempdir)))

(defn find-jar-files
  [dir]
  (fs/list dir {:filter-fn (fn [path] (and (fs/file? path)
                                           (re-find #"\.jar$" (str path))))}))

(defn merge-extracted-jar-files
  [paths target-dir]
  (doseq [path paths]
    (move-source-files path target-dir)))

(defn select-task-cli-spec
  [qualified-task ids]
  (cli-utils/select-cli-specs (task/task-cli-opts qualified-task) ids))

(defn uberjar
  {:shoe/cli-specs (concat (select-task-cli-spec `aot-task/aot #{:aot :all :target})
                           (select-task-cli-spec `jar-task/jar #{:main :name})
                           [[nil "--to-directory PATH" "Output the uberjar contents to a directory instead of a JAR."]])}
  [{:keys [target to-directory] :as task-opts}]
  ;; originally we tried to get the current classpath and remove anything with shoe
  ;; from it. this will not work because those shoe deps may have dependencies
  ;; themselves which should not be on the classpath. Instead we need to read in
  ;; the deps.edn file and determine the classpath ourselves.

  (let [jar-contents-dir (str (or to-directory
                                  (fs/path target (str "uberjar_" (java.util.UUID/randomUUID)))))]
    ;; cleanup our directory when finished
    (when-not to-directory
      (util/add-shutdown-hook (fn [] (fs/delete jar-contents-dir))))

    ;; compile to :target/classes
    (aot-task/aot (select-keys task-opts [:aot :all :target]))
    (fs/move (fs/path target "classes") jar-contents-dir)

    ;; move assets on classpath into libdir directory
    (libdir-task/libdir {:out jar-contents-dir})
    (let [jar-files (find-jar-files jar-contents-dir)
          temp-dirs (explode-jars-to-temp-dirs jar-files)]
      ;; remove jar-files from jar-contents-dir - we have no use for them now.
      (doseq [path jar-files]
        (fs/delete path))
      ;; move all the files we extracted from the jars into the jar-contents-dir
      (merge-extracted-jar-files temp-dirs jar-contents-dir))

    ;; create the JAR
    (when-not to-directory
      (let [jar-path (fs/path (:target task-opts) (:name task-opts))]
        (fs/delete jar-path)
        (log/info "Creating" jar-path "...")
        (fs/jar jar-contents-dir jar-path
                (jar-task/manifest-map {:main (:main task-opts)}))))))