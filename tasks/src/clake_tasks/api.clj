(ns clake-tasks.api
  (:require
    [clojure.spec.alpha :as s]))

(s/def :clake/cli-opts vector?)
(s/def ::attr-map (s/keys :req [:clake/cli-opts]))
(s/def ::deftask-args (s/cat :docstring (s/? string?)
                             :attr-map ::attr-map
                             :argv vector?
                             :body (s/* any?)))

(defmacro deftask
  [task-name & args]
  (let [{:keys [docstring attr-map argv body]} (s/conform ::deftask-args args)]
    `(defn ~task-name
       ~@(when docstring [docstring])
       ~(select-keys attr-map [:clake/cli-opts])
       ~argv
       ~@body)))