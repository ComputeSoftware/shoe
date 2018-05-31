(ns clake-tasks.api
  (:require
    [clojure.spec.alpha :as s]
    [clojure.set :as sets]
    #?(:clj
    [clake-tasks.util :as util])))

(s/def :clake/cli-specs vector?)
(s/def :clake/shutdown-fn any?)
(s/def ::attr-map (s/keys :req [:clake/cli-specs] :opt [:clake/shutdown-fn]))
(s/def ::deftask-args (s/cat :docstring (s/? string?)
                             :attr-map ::attr-map
                             :argv vector?
                             :body (s/* any?)))

(defn get-task-opts
  [context qualified-task-name]
  (let [config (:clake/config context)
        qualified-lookup (sets/map-invert (:refer-tasks config))
        task-name-key (get qualified-lookup qualified-task-name qualified-task-name)
        config-opts (get-in config [:task-opts task-name-key])
        cli-opts (-> (filter #(= qualified-task-name (:clake-task/qualified-name %))
                             (:clake/tasks context))
                     (first)
                     :clake-task/cli-opts)]
    (merge config-opts cli-opts)))

#?(:clj
   (defmacro deftask
     [task-name & args]
     (let [{:keys [docstring attr-map argv body]} (s/conform ::deftask-args args)]
       (let [shutdown-fn (:clake/shutdown-fn attr-map)
             qualified-task-name (symbol (str *ns*) (str task-name))]
         `(do
            ~@(when shutdown-fn
                [`(util/add-shutdown-hook ~shutdown-fn)])
            (defn ~task-name
              ~@(when docstring [docstring])
              ~(select-keys attr-map [:clake/cli-specs])
              [_# context#]
              ((fn ~argv ~@body) (get-task-opts context# '~qualified-task-name) context#)))))))

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