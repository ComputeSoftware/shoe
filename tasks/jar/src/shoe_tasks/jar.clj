(ns shoe-tasks.jar
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [shoe-common.log :as log]
    [shoe-common.fs :as fs]
    [shoe-common.cli-utils :as cli-utils]))

(defn get-project-paths
  []
  (:paths (fs/get-cwd-deps-edn)))

(def default-manifest
  {:built-by   (System/getProperty "user.name")
   :created-by "Shoe"
   :build-jdk  (System/getProperty "java.version")})

(defn manifest-map
  [manifest]
  (merge default-manifest manifest))

(defn copy-project-paths
  [copy-to-path]
  (let [project-paths (get-project-paths)
        _ (when (empty? project-paths) (log/warn ":paths is empty."))]
    ;; put project paths into jar dir
    (doseq [path project-paths]
      (fs/copy path copy-to-path))
    ;; TODO: write pom.xml
    ))

(defn write-jar-contents-directory
  [jar-contents-path]
  ;; TODO: write pom.xml
  (copy-project-paths jar-contents-path))

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
    (write-jar-contents-directory jar-contents-path)
    ;; write jar
    (fs/jar jar-contents-path final-jar-path (manifest-map {:main (:main task-opts)}))
    (log/info "Cleaning up ...")
    (fs/delete jar-contents-path)))