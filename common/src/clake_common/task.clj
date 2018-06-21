(ns clake-common.task
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clake-common.util :as util]
    [clake-common.log :as log]
    [clake-common.shell :as shell]
    [clake-common.script.built-in-tasks :as tasks]))

(defn exit?
  "Returns true if `x` is an exit map."
  [x]
  (some? (:clake-exit/status x)))

(defn exit
  "Return a map that can be passed to `system-exit` to exit the process."
  ([ok?-or-status] (exit ok?-or-status nil))
  ([ok?-or-status msg]
   (let [status (if (number? ok?-or-status)
                  ok?-or-status
                  (if ok?-or-status 0 1))
         ok? (= status 0)
         exit-map (cond-> {:clake-exit/status status
                           :clake-exit/ok?    ok?}
                          msg (assoc :clake-exit/message msg))]
     exit-map)))

(def config-name "clake.edn")

(defn normalize-config
  "Qualifies all symbols in the config so working with the config is a bit easier."
  [config]
  (let [config (update config :refer-tasks
                       (fn [refer-tasks]
                         (merge tasks/default-refer-tasks refer-tasks)))]
    (-> config
        (update :task-opts
                (fn [task-opts]
                  (reduce-kv (fn [task-opts short-task-sym qualified-task-sym]
                               (let [opts (or (get task-opts qualified-task-sym)
                                              (get task-opts short-task-sym))]
                                 (cond-> task-opts
                                         opts (assoc qualified-task-sym opts
                                                     short-task-sym opts))))
                             task-opts (:refer-tasks config)))))))

(defn load-config
  ([] (load-config config-name))
  ([path]
   (when (.exists (io/file path))
     (normalize-config (edn/read-string (slurp path))))))

(defn qualify-task
  "Returns a qualified symbol pointing to the task function or `nil` if
  `task-name` could not be qualified. First tries to qualify the task via a
  `config` lookup. If that fails then tries to lookup in built-in map."
  [config task-name]
  (if (qualified-symbol? task-name)
    task-name
    (or (get-in config [:refer-tasks task-name])
        (get tasks/default-refer-tasks task-name))))

(defn task-options
  [config qualified-task-sym]
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
      (exit true (->> [(when task-doc [task-doc ""])
                             "Options:"
                             summary]
                            (filter some?)
                            (flatten)
                            (str/join "\n")))
      errors
      (exit false (str/join "\n" errors))
      :else options)))

(defn execute-task-handler
  [qualified-task-name args]
  (let [task-var (resolve qualified-task-name)
        meta-map (meta task-var)
        r (validate-args args (:clake/cli-specs meta-map) (:doc meta-map))]
    (shell/system-exit
      (if-not (exit? r)
        (let [r (@task-var (merge (task-options (load-config) qualified-task-name) r))]
          (if (exit? r)
            r
            (exit true)))
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