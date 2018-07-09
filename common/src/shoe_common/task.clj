(ns shoe-common.task
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [shoe-common.util :as util]
    [shoe-common.log :as log]
    [shoe-common.shell :as shell]
    [shoe-common.script.built-in-tasks :as tasks]))

(def exit? shell/exit?)

(def exit shell/exit)

(def config-name "shoe.edn")

(defn normalize-config
  "Qualifies all symbols in the config so working with the config is a bit easier."
  [config]
  (let [config (update config :refer-tasks
                       (fn [refer-tasks]
                         (merge tasks/default-refer-tasks refer-tasks)))]
    (cond-> config
            (:task-opts config)
            (update :task-opts
                    (fn [task-opts]
                      (reduce-kv (fn [task-opts short-task-sym qualified-task-sym]
                                   (let [opts (or (get task-opts qualified-task-sym)
                                                  (get task-opts short-task-sym))]
                                     (cond-> task-opts
                                             opts (assoc qualified-task-sym opts)
                                             true (dissoc short-task-sym))))
                                 task-opts (:refer-tasks config)))))))

(defn load-config
  ([] (load-config config-name))
  ([path]
   (when (.exists (io/file path))
     (normalize-config (edn/read-string (slurp path))))))

(defn task-cli-opts
  [qualified-task]
  (when-let [v (resolve qualified-task)]
    (:shoe/cli-specs (meta v))))

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

(s/def :shoe/cli-specs vector?)
(s/def :shoe/shutdown-fn any?)
(s/def ::attr-map (s/keys :req [:shoe/cli-specs] :opt [:shoe/shutdown-fn]))
(s/def ::deftask-args (s/cat :docstring (s/? string?)
                             :attr-map ::attr-map
                             :argv vector?
                             :body (s/* any?)))

(defn validate-args
  [args cli-specs task-doc]
  (let [cli-specs (conj cli-specs ["-h" "--help" "Print the help menu for this task."])
        ;; parse with :no-defaults so we can customize the order the default options are merged.
        {:keys [options arguments errors summary]} (cli/parse-opts args cli-specs :no-defaults true)]
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
      :else {:opts         options
             :default-opts (cli/get-default-options cli-specs)})))

(defn max-number-of-args-for-var
  [v]
  (apply max (map count (:arglists (meta v)))))

(defn execute-task-handler
  [qualified-task-name args]
  (let [task-var (resolve qualified-task-name)
        meta-map (meta task-var)
        {:keys [opts default-opts] :as r} (validate-args args (:shoe/cli-specs meta-map) (:doc meta-map))]
    (shell/system-exit
      (if-not (exit? r)
        (let [task-result (let [arg-count (max-number-of-args-for-var task-var)]
                            ;; if the function takes no args then we call it with no args
                            ;; otherwise we default to a call passing one arg.
                            (if (zero? arg-count)
                              (@task-var)
                              (@task-var (merge default-opts
                                                (task-options (load-config) qualified-task-name)
                                                opts))))]
          (if (exit? task-result)
            task-result
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