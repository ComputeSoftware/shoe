(ns clake-common.script.entrypoint
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.tools.cli :as cli]
    [clake-common.shell :as shell]
    [clake-common.script.built-in-tasks :as tasks])
  (:import (java.nio.file Paths)))

(defn qualify-task
  "Returns a qualified symbol pointing to the task function or `nil` if
  `task-name` could not be qualified. First tries to qualify the task via a
  `config` lookup. If that fails then tries to lookup in built-in map."
  [config task-name]
  (if (qualified-symbol? task-name)
    task-name
    (or (get-in config [:refer-tasks task-name])
        (get tasks/default-refer-tasks task-name))))

(defn lookup-task-cli-specs
  "Returns the CLI spec for `task-name-str` if it is available."
  [qualified-task]
  (or (get tasks/cli-specs qualified-task)
      (when-let [v (resolve qualified-task)]
        (:clake/cli-specs (meta v)))))

(defn validate-task-cli-opts
  [config args cli-specs]
  ;; TODO: merge in a default help menu here
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-specs :in-order true)]
    (cond
      errors (shell/exit false (str/join \n errors))
      :else arguments)))

(defn task-clojure-command
  [qualified-task cli-args extra-deps aliases]
  ;; we cant simply eval (qualified-task parsed-cli-opts) because the parsed CLI
  ;; opts may not be serializable as a string. Instead we parse the CLI opts at
  ;; runtime a second time and then call the function.
  {:aliases   aliases
   :deps-edn  {:deps
               (if (tasks/built-in? qualified-task)
                 (let [common-dep (get extra-deps 'clake-common)
                       task-dep-name (symbol (str "clake-tasks." (name qualified-task)))]
                   (merge
                     extra-deps
                     {task-dep-name
                      (cond
                        (:local/root common-dep)
                        {:local/root (.getAbsolutePath
                                       ;; we are passed the location of the /common folder
                                       ;; and need to determine the absolute path for the root
                                       ;; of the project
                                       (io/file (-> (:local/root common-dep)
                                                    (Paths/get (make-array String 0))
                                                    (.toAbsolutePath)
                                                    (.normalize)
                                                    (.toFile)
                                                    (.getParentFile)
                                                    (.getAbsolutePath))
                                                "tasks"
                                                (name qualified-task)))}
                        (:git/url common-dep)
                        {:git/url   "https://github.com/ComputeSoftware/clake"
                         :deps/root (str "tasks/" (name qualified-task))
                         :sha       (:sha common-dep)})}))
                 extra-deps)}
   :eval-code `[(require 'clake-common.task)
                (require '~(symbol (namespace qualified-task)))
                (let [v# (resolve '~qualified-task)
                      r# (clake-common.task/execute-task-handler
                           ~qualified-task
                           (:clake/cli-specs (meta v#))
                           ~cli-args
                           @v#)]
                  (shell/system-exit (if (shell/exit? r#)
                                       r#
                                       (shell/exit true))))]
   :cmd-opts  {:stdio ["pipe" "inherit" "inherit"]}})

(defn parse-cli-args
  "Returns a vector of maps with keys :task and :args. :task is a qualified symbol
  of the task function to run. :args is a vector of command line arguments that
  were passed to the task."
  [config args]
  (loop [args args
         cmds []]
    (if-not (empty? args)
      (let [task-name-str (first args)]
        (if-let [qualified-task (qualify-task config (symbol task-name-str))]
          (let [args-without-task (rest args)
                task-cli-specs (lookup-task-cli-specs qualified-task)
                next-args-or-exit (validate-task-cli-opts config args-without-task task-cli-specs)]
            (if-not (shell/exit? next-args-or-exit)
              (recur next-args-or-exit (conj cmds {:task qualified-task
                                                   :args (vec (take (- (count args-without-task)
                                                                       (count next-args-or-exit))
                                                                    args-without-task))}))
              next-args-or-exit))
          (shell/exit false (str "Could not resolve task " task-name-str "."))))
      cmds)))

(defn execute
  [{:keys [extra-deps args aliases]}]
  (let [config {}
        parsed-args (parse-cli-args config args)]
    (if-not (shell/exit? parsed-args)
      (loop [parsed-args parsed-args]
        (if-not (empty? parsed-args)
          (let [{:keys [task args]} (first parsed-args)
                result (shell/clojure-deps-command (task-clojure-command task args extra-deps aliases))]
            (if (shell/status-success? result)
              (recur (rest parsed-args))
              (shell/exit (:exit result) (:err result))))
          (shell/exit true)))
      parsed-args)))

(defn -main
  [& args]
  (let [init-opts (edn/read-string (first args))
        result (execute init-opts)]
    (shell/system-exit result)))