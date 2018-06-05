(ns clake-tasks.tasks.pom
  (:require
    [clake-tasks.util :as util]))

(defn gen-base-pom-string
  [group-id artifact-id version]
  (format
    "<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">
      <modelVersion>4.0.0</modelVersion>
      <groupId>%s</groupId>
      <artifactId>%s</artifactId>
      <version>%s</version>
      <name>%s</name>
    </project>"
    group-id artifact-id version artifact-id))

(defn pom
  [{:keys [project version aliases]} context]
  (let [project (or project (-> (System/getProperty "user.dir") util/file-name symbol))
        pom-str (gen-base-pom-string (or (namespace project) (name project))
                                     (name project)
                                     version)]
    ))