(ns shoe-common.specs
  (:require
    [clojure.spec.alpha :as s]))

;; ==========================
;; Config
;; ==========================
(s/def :shoe-config/task-opts (s/map-of (s/or :sym symbol? :string string?) map?))
(s/def :shoe-config/target-path string?)
(s/def :shoe-config/refer-tasks (s/map-of symbol? qualified-symbol?))
(s/def :shoe/config (s/keys :opt-un [:shoe-config/task-opts
                                      :shoe-config/target-path
                                      :shoe-config/refer-tasks]))

;; ==========================
;; Task context
;; ==========================
;; fully qualified task name
(s/def :shoe-task/qualified-name qualified-symbol?)
;; task name
(s/def :shoe-task/name symbol?)
;; the actual task function
(s/def :shoe-task/fn fn?)
;; the tools.cli specs map
(s/def :shoe-task/cli-specs vector?)
;; the parsed cli opts map
(s/def :shoe-task/cli-opts map?)
(s/def :shoe/task-context (s/keys :req [:shoe-task/qualified-name
                                         :shoe-task/name
                                         :shoe-task/fn
                                         :shoe-task/cli-specs]
                                   :opt [:shoe-task/cli-opts]))

;; ==========================
;; Context
;; ==========================
(s/def ::tasks (s/coll-of :shoe/task-context :kind vector?))
;; collection of all tasks contexts parsed from the command
(s/def :shoe/tasks ::tasks)
;; collection of task contexts that will run after the current task
(s/def :shoe/next-tasks ::tasks)
;; collection of raw command line arguments passed for all tasks
(s/def :shoe/task-cli-args (s/coll-of string? :kind vector?))
;; map of parsed cli opts for shoe itself
(s/def :shoe/cli-opts map?)
;; the deps.edn file used to start the JVM
(s/def :shoe/deps-edn map?)
(s/def :shoe/context (s/keys :req [:shoe/config :shoe/deps-edn :shoe/cli-opts]
                              :opt [:shoe/tasks
                                    :shoe/next-tasks
                                    :shoe/task-cli-args]))

;; ==========================
;; Exit message
;; ==========================
(s/def :shoe-exit/message (s/nilable string?))
(s/def :shoe-exit/ok? boolean?)
(s/def :shoe-exit/status boolean?)
(s/def :shoe/exit (s/keys :req-un [:shoe-exit/status]
                           :opt-un [:shoe-exit/ok? :shoe-exit/message]))