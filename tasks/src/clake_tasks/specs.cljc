(ns clake-tasks.specs
  (:require
    [clojure.spec.alpha :as s]))

;; ==========================
;; Config
;; ==========================
(s/def :clake-config/task-opts (s/map-of (s/or :sym symbol? :string string?) map?))
(s/def :clake-config/target-path string?)
(s/def :clake-config/refer-tasks (s/map-of symbol? qualified-symbol?))
(s/def :clake/config (s/keys :opt-un [:clake-config/task-opts
                                      :clake-config/target-path
                                      :clake-config/refer-tasks]))

;; ==========================
;; Task context
;; ==========================
;; fully qualified task name
(s/def :clake-task/qualified-name qualified-symbol?)
;; task name
(s/def :clake-task/name symbol?)
;; the actual task function
(s/def :clake-task/fn fn?)
;; the tools.cli specs map
(s/def :clake-task/cli-specs vector?)
;; the parsed cli opts map
(s/def :clake-task/cli-opts map?)
(s/def :clake/task-context (s/keys :req [:clake-task/qualified-name
                                         :clake-task/name
                                         :clake-task/fn
                                         :clake-task/cli-specs]
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
(s/def :clake-exit/message (s/nilable string?))
(s/def :clake-exit/ok? boolean?)
(s/def :clake-exit/status boolean?)
(s/def :clake/exit (s/keys :req-un [:clake-exit/status]
                           :opt-un [:clake-exit/ok? :clake-exit/message]))