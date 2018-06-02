(ns clake-common.task
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clake-common.util :as util]
    [clake-common.log :as log]
    [clake-common.shell :as shell]))

(def config-name "clake.edn")

(defn load-config
  []
  (when (.exists (io/file config-name))
    (edn/read-string (slurp config-name))))

(defn task-options
  [config qualified-task-sym]
  ;; TODO: this should also check :refer-tasks before looking up :task-opts
  (get-in config [:task-opts qualified-task-sym]))

(s/def :clake/cli-specs vector?)
(s/def :clake/shutdown-fn any?)
(s/def ::attr-map (s/keys :req [:clake/cli-specs] :opt [:clake/shutdown-fn]))
(s/def ::deftask-args (s/cat :docstring (s/? string?)
                             :attr-map ::attr-map
                             :argv vector?
                             :body (s/* any?)))

(defn validate-args
  [args cli-opts-string]
  (let [{:keys [options arguments errors summary]} (cli/parse-opts args cli-opts-string)]
    (cond
      (:help options)
      (shell/exit true (->> ["Options:" summary]
                            (str/join \n)))
      errors
      (shell/exit false (str/join \n errors))
      :else options)))

(defn system-exit
  [{:clake-exit/keys [status message]}]
  (when message
    (if (= status 0)
      (log/info message)
      (log/error message)))
  (System/exit status))

(defn execute-task-handler
  [qualified-task-name cli-specs args handler]
  (let [r (validate-args args cli-specs)]
    (if-not (shell/exit? r)
      (handler (merge (task-options (load-config) qualified-task-name) r))
      (system-exit r))))

(defn- task-cli-handler-form
  [fn-sym cli-specs task-sym]
  `(defn ~fn-sym
     [& args#]
     (execute-task-handler '~(symbol (str *ns*) (str task-sym))
                           ~cli-specs
                           args#
                           ~task-sym)))

(defmacro def-task-cli-handler
  [fn-name task-sym]
  (task-cli-handler-form fn-name
                         (:clake/cli-specs (meta (resolve task-sym)))
                         task-sym))

(defmacro def-task-main
  [task-sym]
  `(def-task-cli-handler ~'-main ~task-sym))

;; can use *command-line-args*
(defmacro deftask
  [task-name & args]
  (let [{:keys [docstring attr-map argv body]} (s/conform ::deftask-args args)]
    (let [shutdown-fn (:clake/shutdown-fn attr-map)
          ;qualified-task-name (symbol (str *ns*) (str task-name))
          ]
      `(do
         ~@(when shutdown-fn
             [`(util/add-shutdown-hook ~shutdown-fn)])
         (defn ~task-name
           ~@(when docstring [docstring])
           ~(select-keys attr-map [:clake/cli-specs])
           ~argv
           ~@body)))))