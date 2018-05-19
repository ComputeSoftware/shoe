(ns clake-tasks.util
  (:require
    [clojure.java.io :as io]
    [clojure.tools.namespace.find :as ns.find])
  (:import (clojure.lang IFn)))

(defn add-shutdown-hook
  [handler]
  (.addShutdownHook (Runtime/getRuntime) (Thread. ^IFn handler)))

(defn namespaces-in-project
  "Returns a sequence of symbol names for the namespaces in the `:paths` and
  `:extra-paths`, of select aliases, in `deps-edn`."
  [deps-edn aliases]
  (let [alias-paths (mapcat :extra-paths (-> deps-edn
                                             :aliases
                                             (select-keys aliases)
                                             vals))
        paths (set (concat (:paths deps-edn) alias-paths))]
    (ns.find/find-namespaces (map io/file paths))))