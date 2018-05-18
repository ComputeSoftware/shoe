(ns clake-tasks.specs
  (:require
    [clojure.spec.alpha :as s]))

;; ==========================
;; Config
;; ==========================
(s/def :clake-config/task-opts (s/map-of (s/or :sym symbol? :string string?) map?))
(s/def :clake/config (s/keys :opt-un [:clake-config/task-opts]))

;; ==========================
;; Task context
;; ==========================
;; the actual task function
(s/def :clake-task/fn fn?)
;; the tools.cli specs map
(s/def :clake-task/cli-specs vector?)
;; the parsed cli opts map
(s/def :clake-task/cli-opts map?)
(s/def :clake/task-context (s/keys :req [:clake-task/fn :clake-task/cli-specs]
                                   :opt [:clake-task/cli-opts]))

;; ==========================
;; Context
;; ==========================
(s/def ::tasks (s/coll-of :clake/task-context :kind vector?))
;; collection of all tasks contexts parsed from the command
(s/def :clake/tasks ::tasks)
;; collection of task contexts that will run after the current task
(s/def :clake/next-tasks ::tasks)
;; collection of raw command line arguments passed for all tasks
(s/def :clake/task-cli-args (s/coll-of string? :kind vector?))
;; map of parsed cli opts for clake itself
(s/def :clake/cli-opts map?)
;; the deps.edn file used to start the JVM
(s/def :clake/deps-edn map?)
(s/def :clake/context (s/keys :req [:clake/config :clake/deps-edn :clake/cli-opts]
                              :opt [:clake/tasks
                                    :clake/next-tasks
                                    :clake/task-cli-args]))

;; ==========================
;; Exit message
;; ==========================
(s/def :exit/exit-message (s/nilable string?))
(s/def :exit/ok? boolean?)
(s/def :clake/exit (s/keys :req-un [:exit/exit-message :exit/ok?]))