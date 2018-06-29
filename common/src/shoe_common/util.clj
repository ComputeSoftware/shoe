(ns shoe-common.util
  (:require [clojure.string :as str])
  (:import (clojure.lang IFn)))

(defn add-shutdown-hook
  [handler]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^IFn handler)))

(defn symbol-from-var
  [v]
  (let [{:keys [name ns]} (meta v)]
    (symbol (str ns) (str name))))

(defn parse-classpath-string
  "Returns a vector of classpath paths."
  [cp-string]
  (str/split cp-string (re-pattern (System/getProperty "path.separator"))))