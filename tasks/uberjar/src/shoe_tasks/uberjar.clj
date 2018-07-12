(ns shoe-tasks.uberjar
  (:require
    [shoe-common.fs :as fs]
    [shoe-common.task :as task]
    [shoe-common.cli-utils :as cli-utils]
    [shoe-common.log :as log]
    [shoe-common.util :as util]
    [shoe-tasks.jar :as jar-task]
    [shoe-tasks.libdir :as libdir-task]
    [shoe-tasks.aot :as aot-task]))

(defn select-task-cli-spec
  [qualified-task ids]
  (cli-utils/select-cli-specs (task/task-cli-opts qualified-task) ids))

(defn uberjar
  {:shoe/cli-specs (concat (select-task-cli-spec `aot-task/aot #{:aot :all :target})
                           (select-task-cli-spec `jar-task/jar #{:main :name})
                           [[nil "--to-directory PATH" "Output the uberjar contents to a directory instead of a JAR."]])}
  [{:keys [target to-directory] :as task-opts}]
  ;; originally we tried to get the current classpath and remove anything with shoe
  ;; from it. this will not work because those shoe deps may have dependencies
  ;; themselves which should not be on the classpath. Instead we need to read in
  ;; the deps.edn file and determine the classpath ourselves.

  (let [jar-contents-dir (str (or to-directory
                                  (fs/path target (str "uberjar_" (java.util.UUID/randomUUID)))))]
    ;; cleanup our directory when finished
    (when-not to-directory
      (util/add-shutdown-hook (fn [] (fs/delete jar-contents-dir))))

    ;; compile to :target/classes
    (aot-task/aot (select-keys task-opts [:aot :all :target]))
    (fs/move (fs/path target "classes") jar-contents-dir)

    ;; move assets on classpath into libdir directory
    (libdir-task/libdir {:out jar-contents-dir})

    ;; create the JAR
    (when-not to-directory
      (let [jar-path (fs/path (:target task-opts) (:name task-opts))]
        (fs/delete jar-path)
        (log/info "Creating" jar-path "...")
        (fs/jar jar-contents-dir jar-path
                (jar-task/manifest-map {:main (:main task-opts)}))))))