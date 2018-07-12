(ns shoe-tasks.uberjar
  (:require
    [shoe-common.fs :as fs]
    [shoe-common.task :as task]
    [shoe-common.cli-utils :as cli-utils]
    [shoe-common.log :as log]
    [shoe-tasks.jar :as jar-task]
    [shoe-tasks.libdir :as libdir-task]
    [shoe-tasks.aot :as aot-task]))

(defn select-task-cli-spec
  [qualified-task ids]
  (cli-utils/select-cli-specs (task/task-cli-opts qualified-task) ids))

(defn uberjar
  {:shoe/cli-specs (concat (select-task-cli-spec `aot-task/aot #{:aot :all :target})
                           (select-task-cli-spec `jar-task/jar #{:main :name}))}
  [{:keys [target] :as task-opts}]
  ;; originally we tried to get the current classpath and remove anything with shoe
  ;; from it. this will not work because those shoe deps may have dependencies
  ;; themselves which should not be on the classpath. Instead we need to read in
  ;; the deps.edn file and determine the classpath ourselves.

  (let [jar-contents-dir (str (fs/path target (str "uberjar_" (java.util.UUID/randomUUID))))]

    ;; compile to :target/classes
    (aot-task/aot (select-keys task-opts [:aot :all :target]))
    (fs/move (fs/path target "classes") jar-contents-dir)

    ;; move assets on classpath into libdir directory
    (libdir-task/libdir {:out jar-contents-dir})

    ;; create the jar assets
    (jar-task/write-jar-contents-directory jar-contents-dir)

    ;; create the JAR
    (log/info "Creating jar...")
    (fs/jar jar-contents-dir (fs/path (:target task-opts) (:name task-opts))
            (merge jar-task/default-manifest
                   {:main (:main task-opts)}))

    ;; cleanup
    (fs/delete jar-contents-dir)))