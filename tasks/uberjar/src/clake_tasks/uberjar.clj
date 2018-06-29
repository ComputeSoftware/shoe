(ns clake-tasks.uberjar
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.tools.namespace.find :as ns.find]
    [hara.io.file :as fs]
    [hara.io.archive :as archive]
    [clake-common.log :as log]
    [clake-common.shell :as shell]))

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

(defn parse-classpath-string
  "Returns a vector of classpath paths."
  [cp-string]
  (str/split cp-string (re-pattern (System/getProperty "path.separator"))))

(defn current-classpath-string
  "Returns the raw Java classpath string for the current JVM."
  []
  (System/getProperty "java.class.path"))

(defn filter-classpath
  [classpath-str exclude]
  (let [cp-vec (parse-classpath-string classpath-str)]
    (filter (fn [path]
              (not (re-find (re-pattern exclude) path))) cp-vec)))

(defn classpath-string-from-clj
  []
  ;; TODO: include aliases here
  (let [r (shell/clojure-deps-command {:command :path})]
    (when (shell/status-success? r)
      (str/trim-newline (:out r)))))

(defn find-project-paths
  "Returns a vector of paths that are within this project."
  [classpath-vec cwd]
  (into []
        (comp
          (map (fn [path] (.normalize (fs/path path))))
          (filter (fn [path]
                    (str/starts-with? path cwd)))
          (map str))
        classpath-vec))

(defn write-manifest
  [base-dir main]
  (spit (str (fs/path base-dir "META-INF/MANIFEST.MF"))
        (generate-manifest-string {:main-class (str (munge main))})))

(defn uberjar-compile
  [project-paths {:keys [main aot target-path]}]
  (let [compile-path (fs/path target-path "classes")
        namespaces-in-project (set (ns.find/find-namespaces (map io/file project-paths)))
        namespaces-to-compile (set (if (= aot :all) namespaces-in-project aot))]
    (when (and (contains? namespaces-in-project main)
               (not (contains? namespaces-to-compile main)))
      (log/warn "The namespace set as :main " main " is not set to be AOT compiled."))
    ;; ensure our compile path is created to avoid CompilerException
    (fs/create-directory compile-path)
    (binding [*compile-path* (str compile-path)]
      (doseq [ns-sym namespaces-to-compile]
        (log/info "Compiling" ns-sym "...")
        (compile ns-sym)))))

(defn uberjar
  {:clake/cli-specs [["-t" "--target-path PATH" "Path for compilation."
                      :default "target"]
                     ["-m" "--main NS" "The main namespace"
                      :parse-fn symbol]
                     ["-j" "--jar-name NAME" "The name of the uberjar."
                      :default "standalone.jar"]
                     ["-a" "--aot NAMESPACES" "Comma separated list of namespaces to compile."
                      :parse-fn (fn [s]
                                  (map symbol (str/split s #",")))]
                     [nil "--aot-all" "Set to true to AOT all project files."]]}
  [{:keys [target-path main jar-name aot aot-all]}]
  ;; originally we tried to get the current classpath and remove anything with clake
  ;; from it. this will not work because those clake deps may have dependencies
  ;; themselves which should not be on the classpath. Instead we need to read in
  ;; the deps.edn file and determine the classpath ourselves.
  (let [cp-vec (parse-classpath-string (classpath-string-from-clj))
        cwd (System/getProperty "user.dir")
        jar-contents-path (fs/path target-path "jar-contents")
        jar-path (fs/path target-path jar-name)]
    ;; move all assets on the classpath into the JAR
    (explode-classpath cp-vec jar-contents-path)
    ; compile
    (let [aot-val (if aot-all :all aot)]
      (uberjar-compile (find-project-paths cp-vec cwd)
                       {:main        main
                        :aot         aot-val
                        :target-path target-path}))
    ;; copy compiled classes into the jar contents directory
    (log/info "Moving resources...")
    (fs/move (fs/path target-path "classes") jar-contents-path)
    ;; add the manifest to the jar
    (write-manifest jar-contents-path main)
    ;; create the jar
    (log/info "Creating" jar-name "...")
    (fs/delete jar-path)
    (archive/archive jar-path jar-contents-path)
    ;; cleanup

    true))