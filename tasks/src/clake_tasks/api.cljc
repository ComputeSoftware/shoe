(ns clake-tasks.api
  (:require
    [clojure.spec.alpha :as s]
    #?(:clj
    [clake-tasks.util :as util])))

(s/def :clake/cli-specs vector?)
(s/def :clake/shutdown-fn any?)
(s/def ::attr-map (s/keys :req [:clake/cli-specs] :opt [:clake/shutdown-fn]))
(s/def ::deftask-args (s/cat :docstring (s/? string?)
                             :attr-map ::attr-map
                             :argv vector?
                             :body (s/* any?)))

#?(:clj
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
              ~@body))))))

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