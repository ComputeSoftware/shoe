(ns clake-tasks.tasks.uberjar
  (:require
    [clojure.string :as str]
    [clojure.set :as sets]
    [clojure.edn :as edn]
    [clojure.java.shell :as sh]
    [hara.io.file :as fs]
    [hara.io.archive :as archive]
    [clake-tasks.util :as util]))

(defn file-name
  [path]
  (str (fs/relativize (fs/parent path) path)))

(defn trim-beginning-slash
  [s]
  (if (str/starts-with? s "/")
    (subs s 1)
    s))

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
  (println (str "Could not copy classpath entry: " src)))

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

;; extract to directories
;; move into final directory & perform mergers
;; create archive
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

(defn generate-manifest-string
  [{:keys [manifest-version built-by created-by build-jdk main-class]
    :or   {manifest-version "1.0"
           built-by         (System/getProperty "user.name")
           created-by       "Clake"
           build-jdk        (System/getProperty "java.version")}}]
  ;; NOTE: A manifest file MUST end with a newline
  (format (str "Manifest-Version: %s\n"
               "Built-By: %s\n"
               "Created-By: %s\n"
               "Build-Jdk: %s\n"
               "Main-Class: %s\n")
          manifest-version
          built-by
          created-by
          build-jdk
          main-class))

(defn classpath-string
  "Returns the raw Java classpath string."
  [aliases]
  ;; should probably replace this we an actual API call to tools-deps. there's
  ;; no easy built-in function to do this right now though.
  (let [{:keys [exit out]} (sh/sh "clojure" (str "-A" (str/join aliases)) "-Spath")]
    (when (= 0 exit)
      (str/trim-newline out))))

(defn parse-classpath-string
  "Returns a vector of classpath paths."
  [cp-string]
  (str/split cp-string (re-pattern (System/getProperty "path.separator"))))

(defn write-manifest
  [base-dir main]
  (spit (str (fs/path base-dir "META-INF/MANIFEST.MF"))
        (generate-manifest-string {:main-class (str (munge main))})))

(defn uberjar-compile
  [{:keys [aot aliases]} {:keys [config deps-edn]}]
  (let [target-path (:target-path config)
        compile-path (fs/path target-path "classes")
        namespaces-to-compile (set (if (= aot :all)
                                     (util/namespaces-in-project deps-edn aliases)
                                     aot))]
    ;; ensure our compile path is created to avoid CompilerException
    (fs/create-directory compile-path)
    (binding [*compile-path* (str compile-path)]
      (doseq [ns-sym namespaces-to-compile]
        (compile ns-sym)))))

(defn uberjar
  [{:keys [aliases main jar-name] :as opts} {:keys [config] :as ctx}]
  (let [target-path (:target-path config)
        cp-vec (parse-classpath-string (classpath-string aliases))
        jar-contents-path (fs/path target-path "jar-contents")
        _ (explode-classpath cp-vec jar-contents-path)]
    (uberjar-compile opts ctx)
    ;; copy compiled classes into the jar contents directory
    (fs/move (fs/path target-path "classes") jar-contents-path)
    ;; add the manifest to the jar
    (write-manifest jar-contents-path main)
    ;; create the jar
    (archive/archive (fs/path target-path (or jar-name "standalone.jar")) jar-contents-path)
    ;; cleanup
    (fs/delete jar-contents-path)
    true))