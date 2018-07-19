(ns shoe-common.fs
  (:refer-clojure :exclude [resolve list])
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [clojure.string :as str])
  (:import (java.io FileNotFoundException File)
           (java.nio.file Files OpenOption Path Paths LinkOption FileVisitor FileVisitResult CopyOption StandardCopyOption FileSystems FileSystem)
           (java.net URI)
           (java.nio.file.attribute FileAttribute)
           (java.util Map)
           (java.util.jar JarOutputStream JarEntry Manifest Attributes$Name)))

(def cwd (System/getProperty "user.dir"))

(defn path
  "Returns a java.nio.file.Path given the path parts `paths`."
  ([path]
   (cond
     (instance? Path path) path
     (string? path) (Paths/get path (make-array String 0))))
  ([path & paths]
   (let [paths (map str paths)]
     (Paths/get (str path) (into-array String paths)))))

(defn new-output-stream
  [p]
  (Files/newOutputStream (path p) (make-array OpenOption 0)))

(defn new-input-stream
  [p]
  (Files/newInputStream (path p) (make-array OpenOption 0)))

(defn to-file
  [p]
  (.toFile (path p)))

(defn absolute-path
  [p]
  (.toAbsolutePath (path p)))

(defn real-path
  [p]
  (.toRealPath (path p) (make-array LinkOption 0)))

(defn file-name
  [p]
  (.getFileName (path p)))

(defn exists?
  [p]
  (Files/exists (path p) (make-array LinkOption 0)))

(defn directory?
  [p]
  (Files/isDirectory (path p) (make-array LinkOption 0)))

(declare list)

(defn empty-directory?
  [path]
  (and (directory? path)
       (= 1 (count (list path)))))

(defn file?
  [p]
  (Files/isRegularFile (path p) (make-array LinkOption 0)))

(def file-visit-result-lookup
  {:continue      FileVisitResult/CONTINUE
   :skip-siblings FileVisitResult/SKIP_SIBLINGS
   :skip-subtree  FileVisitResult/SKIP_SUBTREE
   :terminate     FileVisitResult/TERMINATE})

(defn walk
  [root
   {:keys [pre-visit-directory
           post-visit-directory
           visit-file
           visit-file-failed]
    :or   {pre-visit-directory  (constantly :continue)
           post-visit-directory (constantly :continue)
           visit-file           (constantly :continue)
           visit-file-failed    (constantly :continue)}}]
  (let [coerce-result (fn coerce-result
                        [r]
                        (or (file-visit-result-lookup r) FileVisitResult/CONTINUE))]
    (Files/walkFileTree
      (path root) #{} Integer/MAX_VALUE
      (reify FileVisitor
        (preVisitDirectory [_ dir attrs]
          (coerce-result (pre-visit-directory dir attrs)))
        (postVisitDirectory [_ dir io-ex]
          (coerce-result (post-visit-directory dir io-ex)))
        (visitFile [_ file attrs]
          (coerce-result (visit-file file attrs)))
        (visitFileFailed [_ file io-ex]
          (coerce-result (visit-file-failed file io-ex)))))))

(defn parent
  [p]
  (.getParent (path p)))

(defn relativize
  [parent child]
  (.relativize (path parent) (path child)))

(defn resolve
  [path1 path2]
  (.resolve (path path1) (path path2)))

(defn create-directories
  [p]
  (let [p (path p)]
    (when (not (exists? p))
      (Files/createDirectories p (make-array FileAttribute 0)))))

(defn ensure-parent-dirs
  [p]
  (let [parent (-> (path p) absolute-path parent)]
    (when-not (exists? parent)
      (create-directories parent))))

(defn list
  ([p] (list p nil))
  ([p {:keys [filter-fn]
       :or   {filter-fn (constantly true)}}]
   (let [fs-list (transient [])
         add-path! (fn [path]
                     (when (filter-fn path)
                       (conj! fs-list path)))]
     (walk (path p) {:pre-visit-directory
                     (fn [dir _]
                       (add-path! dir))
                     :visit-file
                     (fn [file _]
                       (add-path! file))})
     (persistent! fs-list))))

(defn list-files
  [p]
  (list p {:filter-fn file?}))

(defn delete
  [path]
  (walk path
        {:visit-file
         (fn [file _]
           (Files/delete file))
         :post-visit-directory
         (fn [dir _]
           (Files/delete dir))}))

(defn copy-options
  [{:keys [overwrite? copy-attributes?]}]
  (into-array CopyOption
              (cond-> []
                      overwrite? (conj StandardCopyOption/REPLACE_EXISTING)
                      copy-attributes? (conj StandardCopyOption/COPY_ATTRIBUTES))))

(defn nio-copy
  [source target opts]
  (Files/copy (path source) (path target) (copy-options opts)))

;; Java code for copy
;; https://docs.oracle.com/javase/tutorial/displayCode.html?code=https://docs.oracle.com/javase/tutorial/essential/io/examples/Copy.java
(defn copy
  ([source target] (copy source target {}))
  ([source target {:keys [recursive? overwrite?]
                   :or   {recursive? true
                          overwrite? true}}]
   (walk (path source)
         {:pre-visit-directory
          (fn [dir attrs]
            (when-not (exists? dir)
              (let [new-dir (resolve target (relativize source dir))]
                (create-directories new-dir)
                (nio-copy dir new-dir {:overwrite? overwrite?}))))
          :visit-file
          (fn [file attrs]
            (let [relative-path (let [rel (relativize source file)]
                                  ;; if source & file are the same then we copy using the
                                  ;; file's name.
                                  (if (= "" (str rel))
                                    (file-name file)
                                    rel))
                  new-file-path (resolve target relative-path)]
              (ensure-parent-dirs new-file-path)
              (nio-copy file new-file-path {:overwrite? overwrite?})))})))

(defn move
  ([source target] (move source target {}))
  ([source target opts]
   (let [source (path source)
         target (path target)]
     (walk source
           {:visit-file
            (fn [file _]
              (let [new-file-path (resolve target (relativize source file))]
                ;; ensure parent dirs exist
                (ensure-parent-dirs new-file-path)
                (Files/move file new-file-path (copy-options opts))))
            :post-visit-directory
            (fn [dir _]
              (when (empty-directory? dir)
                (delete dir)))}))))

(defn create-temp-directory
  ([] (create-temp-directory nil))
  ([prefix]
   (Files/createTempDirectory prefix (make-array FileAttribute 0))))

(defn ^FileSystem create-zip-filesystem
  [p opts]
  (FileSystems/newFileSystem (URI. (str "jar:file:" (.toAbsolutePath (path p))))
                             ^Map (reduce-kv (fn [acc k v]
                                               (assoc acc (name k) (str v))) {} opts)))

(defn list-archive-files
  "Returns a vector of the files in an archive."
  [archive-path]
  (with-open [zip-fs (create-zip-filesystem (path archive-path) {:create false})]
    (into []
          (mapcat list)
          (.getRootDirectories zip-fs))))

(defn jar-manifest
  "Returns a `Manifest` object given a map of manifest attributes."
  [manifest]
  (let [m (Manifest.)
        attrs (.getMainAttributes m)]
    (.put attrs Attributes$Name/MANIFEST_VERSION "1.0")
    (.put attrs Attributes$Name/MAIN_CLASS (-> (:main manifest)
                                               str
                                               (.replaceAll "-" "_")))
    ;; put all other attributes in manifest
    (doseq [[k v] (dissoc manifest :main)]
      (.put attrs (Attributes$Name. (name k)) v))
    m))

(defn jar
  ([input-path output-path]
   (jar input-path output-path))
  ([input-path output-path manifest]
    ;; ensure output-path parent dirs exist
    ;(create-directories (parent output-path))

   (with-open [jar-os (if manifest
                        (JarOutputStream. (new-output-stream output-path) (jar-manifest manifest))
                        (JarOutputStream. (new-output-stream output-path)))]
     (doseq [path ^Path (list-files input-path)]
       ;; zips & jars have a special format they must adhere to
       ;; see https://stackoverflow.com/questions/1281229/how-to-use-jaroutputstream-to-create-a-jar-file
       (let [jar-path (-> (relativize input-path path)
                          (str)
                          (str/replace "\\" "/"))
             jar-entry (doto (JarEntry. jar-path)
                         (.setTime (.lastModified ^File (to-file path))))]
         (.putNextEntry jar-os jar-entry)
         (Files/copy path jar-os)
         (.closeEntry jar-os))))))

(defn archive
  [input-dir output-path]
  (with-open [zip-fs (create-zip-filesystem output-path {:create true})]
    (doall
      (mapv (fn [p]
              (let [zip-path (.getPath zip-fs (str (relativize input-dir p)) (make-array String 0))]
                (create-directories (parent zip-path))
                (nio-copy p zip-path {:overwrite? true})
                zip-path))
            (list input-dir {:filter-fn file?})))))

(defn extract-archive
  ([archive-path extract-to] (extract-archive archive-path extract-to {}))
  ([archive-path extract-to {:keys [filter-fn]
                             :or   {filter-fn (constantly true)}}]
   (with-open [zip-fs (create-zip-filesystem archive-path {:create false})]
     (into []
           (comp
             (mapcat list)
             (filter filter-fn)
             (map (fn [src-path]
                    (let [out-path (path extract-to (subs (str src-path) 1))
                          out-parent-dirs (if (directory? src-path)
                                            out-path
                                            (parent out-path))]
                      (create-directories out-parent-dirs)
                      (nio-copy src-path out-path {:overwrite? true})))))
           (.getRootDirectories zip-fs)))))

;; makes spit and slurp work with `Path`s.
(extend Path
  io/IOFactory
  (assoc io/default-streams-impl
    :make-input-stream (fn [x opts] (new-input-stream x))
    :make-output-stream (fn [x opts] (new-output-stream x))))

(defn parse-edn-file-at
  "Returns EDN data `slurp`'ed from `path`."
  [path]
  (try
    (edn/read-string (slurp path))
    (catch FileNotFoundException _ nil)))

(defn get-cwd-deps-edn
  []
  (parse-edn-file-at (io/file cwd "deps.edn")))

(defn spit-at-path
  [path content]
  (create-directories (parent path))
  (spit path content))