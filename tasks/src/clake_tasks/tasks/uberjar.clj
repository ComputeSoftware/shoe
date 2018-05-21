(ns clake-tasks.tasks.uberjar
  (:require
    [clojure.string :as str]
    [clojure.java.shell :as sh])
  (:import (java.nio.file FileVisitor OpenOption Files Path FileVisitResult FileSystem FileSystems CopyOption LinkOption)
           (java.util.jar JarOutputStream JarEntry)
           (java.nio.file.attribute FileAttribute)
           (java.io InputStream)))

(defonce ^FileSystem FS (FileSystems/getDefault))

(defn to-path
  ^Path [s]
  (.getPath FS s (make-array String 0)))

(defn copy-file
  [^InputStream in ^Path target]
  (Files/copy in target ^"[Ljava.nio.file.CopyOption;" (make-array CopyOption 0)))

(defn copy-directory
  [^Path src ^Path dest]
  (let [copy-dir
        (reify FileVisitor
          (visitFile [_ p attrs]
            (let [f (.relativize src p)]
              (with-open [is (Files/newInputStream p (make-array OpenOption 0))]
                (copy-file is (.resolve dest f))))
            FileVisitResult/CONTINUE)
          (preVisitDirectory [_ p attrs]
            (Files/createDirectories (.resolve dest (.relativize src p))
                                     (make-array FileAttribute 0))
            FileVisitResult/CONTINUE)
          (postVisitDirectory [_ p ioexc]
            (if ioexc (throw ioexc) FileVisitResult/CONTINUE))
          (visitFileFailed [_ p ioexc] (throw (ex-info "Visit File Failed" {:p p} ioexc))))]
    (Files/walkFileTree src copy-dir)
    :ok))

(defn explode-classpath
  "Creates a directory containing all files on the classpath. `classpath-vec is
  a vector of classpath locations. `target` is the location to explode "
  [classpath-vec ^Path target]
  )

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

(defn write-jar
  "Creates a JAR file at `target` with the contents of `src`. Returns `target`."
  [^Path src ^Path target]
  (with-open [os (-> target
                     (Files/newOutputStream (make-array OpenOption 0))
                     JarOutputStream.)]
    (let [walker (reify FileVisitor
                   (visitFile [_ p attrs]
                     (.putNextEntry os (JarEntry. (.toString (.relativize src p))))
                     (Files/copy p os)
                     FileVisitResult/CONTINUE)
                   (preVisitDirectory [_ p attrs]
                     (when (not= src p)                     ;; don't insert "/" to zip
                       (.putNextEntry os (JarEntry. (str (.relativize src p) "/")))) ;; directories must end in /
                     FileVisitResult/CONTINUE)
                   (postVisitDirectory [_ p ioexc]
                     (if ioexc (throw ioexc) FileVisitResult/CONTINUE))
                   (visitFileFailed [_ p ioexc] (throw ioexc)))]
      (Files/walkFileTree src walker)))
  target)

(defn uberjar
  []
  )