(ns clake-common.task
  (:require
    [clojure.spec.alpha :as s]
    [clojure.string :as str]
    [clojure.tools.cli :as cli]
    [clojure.java.io :as io]
    [clojure.edn :as edn]
    [clake-common.util :as util]
    [clake-common.log :as log]))

(def config-name "clake.edn")

(defn load-config
  []
  (when (.exists (io/file config-name))
    (edn/read-string (slurp config-name))))

(defn task-options
  [config qualified-task-sym]
  (get-in config [:task-opts qualified-task-sym]))

(defn exit?
  [x]
  (some? (:clake-exit/status x)))

(defn exit
  ([ok?-or-status] (exit ok?-or-status nil))
  ([ok?-or-status msg]
   (let [status (if (number? ok?-or-status)
                  ok?-or-status
                  (if ok?-or-status 0 1))
         ok? (= status 0)]
     (cond-> {:clake-exit/status status
              :clake-exit/ok?    ok?}
             msg (assoc :clake-exit/message msg)))))

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
      (exit true (->> ["Options:" summary]
                      (str/join \n)))
      errors
      (exit false (str/join \n errors))
      :else options)))

(defn system-exit
  [{:clake-exit/keys [status message]}]
  (when message
    (if (= status 0)
      (log/info message)
      (log/error message)))
  (System/exit status))

(defn- task-main-form
  [cli-specs handler]
  `(defn ~'-main
     [& args#]
     (let [r# (validate-args args# ~cli-specs)]
       (if (exit? r#)
         (system-exit r#)
         (let [config# (load-config)]
           (~handler (merge (task-options config# ~handler) r#)))))))

(defmacro def-task-main
  [qualified-task-name]
  (task-main-form (:clake/cli-specs (meta (resolve qualified-task-name))) qualified-task-name))

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