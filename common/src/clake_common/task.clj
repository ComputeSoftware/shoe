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
  [args cli-specs task-doc]
  (let [cli-specs (conj cli-specs ["-h" "--help" "Print the help menu for this task."])
        {:keys [options arguments errors summary]} (cli/parse-opts args cli-specs)]
    (cond
      (:help options)
      (shell/exit true (->> [(when task-doc [task-doc ""])
                             "Options:"
                             summary]
                            (filter some?)
                            (flatten)
                            (str/join "\n")))
      errors
      (shell/exit false (str/join "\n" errors))
      :else options)))

(defn execute-task-handler
  [qualified-task-name args]
  (let [task-var (resolve qualified-task-name)
        meta-map (meta task-var)
        r (validate-args args (:clake/cli-specs meta-map) (:doc meta-map))]
    (shell/system-exit
      (if-not (shell/exit? r)
        (let [r (@task-var (merge (task-options (load-config) qualified-task-name) r))]
          (if (shell/exit? r)
            r
            (shell/exit true)))
        r))))

(defmacro def-task-cli-handler
  [fn-sym task-sym]
  `(defn ~fn-sym
     [& args#]
     (execute-task-handler '~(symbol (str *ns*) (str task-sym)) args#)))

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