(ns clake-tasks.fs
  (:import (java.nio.file FileVisitor OpenOption Files Path FileVisitResult FileSystem FileSystems CopyOption LinkOption)
           (java.nio.file.attribute FileAttribute)
           (java.io InputStream BufferedInputStream)
           (java.util.jar JarOutputStream JarEntry JarInputStream)))

(defonce ^FileSystem FS (FileSystems/getDefault))

(defn to-path
  ^Path [s]
  (.getPath FS s (make-array String 0)))

(defn walk-file-tree
  [^Path src {:keys [visit pre post fail]} {:keys [exclude]}]
  (let [visit-result-lookup {:continue      FileVisitResult/CONTINUE
                             :terminate     FileVisitResult/TERMINATE
                             :skip-subtree  FileVisitResult/SKIP_SUBTREE
                             :skip-siblings FileVisitResult/SKIP_SIBLINGS}
        visit-result->enum (fn [x]
                             (get visit-result-lookup x FileVisitResult/CONTINUE))
        visitor-handler (fn [handler path extra-arg]
                          (visit-result->enum
                            (if handler
                              (let [path-str (.toString path)
                                    excluded? (not-every? #(re-find % path-str) exclude)]
                                (when-not excluded?
                                  (handler path extra-arg)))
                              :continue)))]
    (Files/walkFileTree
      src
      (reify FileVisitor
        (visitFile [_ p attrs]
          (visit-result->enum
            (if visit (visit p attrs) :continue)))
        (preVisitDirectory [_ p attrs]
          (visit-result->enum
            (if pre (pre p attrs) :continue)))
        (postVisitDirectory [_ p ioexc]
          (visit-result->enum
            (if post (post p ioexc) :continue)))
        (visitFileFailed [_ p ioexc]
          (visit-result->enum
            (if fail (fail p ioexc) :continue)))))))

(defn consume-jar
  [^Path path handler]
  (with-open [is (-> path
                     (Files/newInputStream (make-array OpenOption 0))
                     BufferedInputStream.
                     JarInputStream.)]
    (loop []
      (when-let [entry (.getNextJarEntry is)]
        (handler is entry)
        (recur)))))

(defn copy-file
  [^InputStream in ^Path target]
  (Files/copy in target ^"[Ljava.nio.file.CopyOption;" (make-array CopyOption 0)))

(defn copy-directory
  [^Path src ^Path dest]
  (walk-file-tree
    src
    {:pre   (fn [path _]
              (Files/createDirectories (.resolve dest (.relativize src path))
                                       (make-array FileAttribute 0)))
     :visit (fn [path _]
              (let [f (.relativize src path)]
                (with-open [is (Files/newInputStream path (make-array OpenOption 0))]
                  (copy-file is (.resolve dest f)))))
     :post  (fn [_ ioexc]
              (when ioexc (throw ioexc)))
     :fail  (fn [path ioexc]
              (throw (ex-info "Visit file failed." {:path path} ioexc)))}))

(defn write-jar
  "Creates a JAR file at `target` with the contents of `src`. Returns `target`."
  [^Path src ^Path target]
  (with-open [os (-> target
                     (Files/newOutputStream (make-array OpenOption 0))
                     JarOutputStream.)]
    (walk-file-tree
      src
      {:pre   (fn [path _]
                ;; don't insert "/" to zip
                (when (not= src path)
                  ;; directories must end in /
                  (.putNextEntry os (JarEntry. (str (.relativize src path) "/")))))
       :visit (fn [path _]
                (.putNextEntry os (JarEntry. (.toString (.relativize src path))))
                (Files/copy path os))
       :post  (fn [_ ioexc] (when ioexc (throw ioexc)))
       :fail  (fn [_ ioexc] (when ioexc (throw ioexc)))})))