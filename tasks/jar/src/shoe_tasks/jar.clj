(ns shoe-tasks.jar
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [shoe-common.log :as log]
    [shoe-common.fs :as fs]
    [shoe-common.cli-utils :as cli-utils]
    [shoe-tasks.archive :as archive-task]))

(defn get-project-paths
  []
  (:paths (fs/get-cwd-deps-edn)))

(defn manifestify-keyword
  [k]
  (str/join "-" (map str/capitalize (str/split (name k) #"-"))))

;; https://docs.oracle.com/javase/6/docs/technotes/guides/jar/jar.html#Manifest%20Specification
(defn manifest-from-map
  [m]
  (str (str/join "\n"
                 (reduce-kv (fn [lines k v]
                              ;; we ignore nil values
                              (if (and v (not (str/blank? v)))
                                (conj lines (format "%s: %s"
                                                    (manifestify-keyword k)
                                                    (str v)))
                                lines))
                            ;; Manifest-Version must come first
                            ["Manifest-Version: 1.0"]
                            m))
       "\n"))

(def default-manifest
  {:built-by   (System/getProperty "user.name")
   :created-by "Shoe"
   :build-jdk  (System/getProperty "java.version")})

(def manifest-path "META-INF/MANIFEST.MF")

(defn write-manifest
  [out-dir main]
  (fs/spit-at-path (fs/path out-dir manifest-path)
                   (manifest-from-map (merge default-manifest
                                             {:main-class (str (munge main))}))))

(defn write-jar-contents-directory
  [jar-contents-path {:keys [main]}]
  (let [project-paths (get-project-paths)
        _ (when (empty? project-paths) (log/warn ":paths is empty."))]
    ;; put project paths into jar dir
    (doseq [path project-paths]
      (fs/copy path jar-contents-path))
    ;; put manifest into jar dir
    (write-manifest jar-contents-path main)
    ;; TODO: write pom.xml
    ))

(defn jar
  "Build a jar file from the project."
  {:shoe/cli-specs [["-t" "--target PATH" "Target path for output."
                     :default "target"]
                    ["-m" "--main NS" "The main ns for your project. Used when writing the manifest."]
                    ["-n" "--name STR" "The name of the outputted jar."
                     :default "project.jar"]]}
  [{:keys    [target]
    jar-name :name
    :as      task-opts}]
  (let [jar-contents-path (fs/path target jar-name)
        final-jar-path (fs/path target jar-name)]
    ;; put jar contents into directory
    (write-jar-contents-directory jar-contents-path task-opts)
    ;; write jar
    (archive-task/archive {:in  (str jar-contents-path)
                           :out (str final-jar-path)})
    (log/info "Cleaning up ...")
    (fs/delete jar-contents-path)))