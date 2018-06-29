(ns clake-tasks.jar
  (:require
    [hara.io.file :as fs]
    [hara.io.archive :as archive]
    [clake-common.log :as log]))

(defn generate-manifest-string
  [{:keys [manifest-version built-by created-by build-jdk main-class]
    :or   {manifest-version "1.0"
           built-by         (System/getProperty "user.name")
           created-by       "Clake"
           build-jdk        (System/getProperty "java.version")}}]
  ;; NOTE: A manifest file MUST end with a newline
  ;; TODO: write function to transform map into this format
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

(defn write-manifest
  [out-dir main]
  (spit (str (fs/path out-dir "META-INF/MANIFEST.MF"))
        (generate-manifest-string {:main-class (str (munge main))})))

(defn jar
  {:clake/cli-specs [["-t" "--target PATH" "Target path for output."
                      :default "target"]
                     ["-m" "--main NS" "The main ns for your project. Used when writing the manifest."]
                     ["-n" "--name STR" "The name of the outputted jar."
                      :default "project.jar"]]}
  [{:keys    [target main]
    jar-name :name}]
  (let [jar-contents-path (fs/path target "jar-contents")
        jar-path (fs/path target jar-name)]
    (write-manifest jar-contents-path main)
    (log/info "Creating" jar-name "...")
    (when (fs/exists? jar-path)
      (log/info jar-path "exists. Overwriting."))
    (fs/delete jar-path)
    (archive/archive jar-path jar-contents-path)
    (log/info "Cleaning up ...")
    (fs/delete jar-contents-path)))