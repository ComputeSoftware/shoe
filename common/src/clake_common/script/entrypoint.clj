(ns clake-common.script.entrypoint
  (:require
    [clojure.string :as str]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clojure.tools.cli :as cli]
    [clake-common.shell :as shell]
    [clake-common.script.built-in-tasks :as tasks])
  (:import (java.nio.file Paths)
           (java.io FileNotFoundException)))

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
  (try
    (require (symbol (namespace qualified-task)))
    (if-let [v (resolve qualified-task)]
      (conj (:clake/cli-specs (meta v)) tasks/cli-task-help-option)
      (shell/exit false (str "Could not resolve task " qualified-task ".")))
    (catch FileNotFoundException ex
      (shell/exit false (.getMessage ex)))))

;; we need to validate the CLI opts at entrypoint to separate out the task calls
(defn validate-task-cli-opts
  [args cli-specs]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-specs :in-order true)]
    (cond
      errors (shell/exit false (str/join \n errors))
      :else arguments)))

(defn built-in-task-coord
  "Returns the coordinate for the built-in task with the qualified name
  `qualified-task`."
  [qualified-task common-dep]
  (let [task-dep-name (symbol (str "clake-tasks." (name qualified-task)))]
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

(defn task-clojure-command
  [qualified-task cli-args extra-deps aliases]
  ;; we cant simply eval (qualified-task parsed-cli-opts) because the parsed CLI
  ;; opts may not be serializable as a string. Instead we parse the CLI opts at
  ;; runtime a second time and then call the function.
  {:aliases   aliases
   :deps-edn  {:deps
               (if (tasks/built-in? qualified-task)
                 (let [common-dep (get extra-deps 'clake-common)]
                   (merge extra-deps (built-in-task-coord qualified-task common-dep)))
                 extra-deps)}
   :eval-code `[(require 'clake-common.task)
                (require '~(symbol (namespace qualified-task)))
                (clake-common.task/execute-task-handler
                  '~qualified-task
                  ~cli-args)]
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
                cli-specs (lookup-task-cli-specs qualified-task)]
            (if-not (shell/exit? cli-specs)
              (let [next-args-or-exit (validate-task-cli-opts args-without-task cli-specs)]
                (if-not (shell/exit? next-args-or-exit)
                  (recur next-args-or-exit (conj cmds {:task qualified-task
                                                       :args (vec (take (- (count args-without-task)
                                                                           (count next-args-or-exit))
                                                                        args-without-task))}))
                  next-args-or-exit))
              cli-specs))
          (shell/exit false (str "Could not resolve task " task-name-str "."))))
      cmds)))

(defn execute
  [{:keys [extra-deps args aliases]}]
  ;; TODO: load config
  (let [config {}
        parsed-args (parse-cli-args config args)]
    (if-not (shell/exit? parsed-args)
      ;; loop to run the commands parsed from the cli args
      (loop [parsed-args parsed-args]
        (if-not (empty? parsed-args)
          (let [{:keys [task args]} (first parsed-args)
                result (shell/clojure-deps-command (task-clojure-command task args extra-deps aliases))]
            ;; stop task execution if one of the tasks fails with a non-zero exit
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