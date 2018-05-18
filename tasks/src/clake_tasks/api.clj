(ns clake-tasks.api
  (:require
    [clojure.spec.alpha :as s]
    [clake-tasks.util :as util]))

(s/def :clake/cli-specs vector?)
(s/def :clake/shutdown-fn any?)
(s/def ::attr-map (s/keys :req [:clake/cli-specs] :opt [:clake/shutdown-fn]))
(s/def ::deftask-args (s/cat :docstring (s/? string?)
                             :attr-map ::attr-map
                             :argv vector?
                             :body (s/* any?)))

(defmacro deftask
  [task-name & args]
  (let [{:keys [docstring attr-map argv body]} (s/conform ::deftask-args args)]
    (let [shutdown-fn (:clake/shutdown-fn attr-map)]
      `(do
         ~@(when shutdown-fn
             [`(util/add-shutdown-hook ~shutdown-fn)])
         (defn ~task-name
           ~@(when docstring [docstring])
           ~(select-keys attr-map [:clake/cli-specs])
           ~argv
           ~@body)))))