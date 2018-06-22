(ns clake-common.util
  (:import (clojure.lang IFn)))

(defn add-shutdown-hook
  [handler]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^IFn handler)))

(defn symbol-from-var
  [v]
  (let [{:keys [name ns]} (meta v)]
    (symbol (str ns) (str name))))

(defmacro bind-exit
  [bindings & body]
  )