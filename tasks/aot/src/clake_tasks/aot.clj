(ns clake-tasks.aot
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.namespace.find :as ns.find]
    [clake-common.log :as log]
    [clake-common.shell :as shell]
    [clake-common.task :as task]
    [clake-common.fs :as fs]
    [clake-common.util :as util])
  (:import (java.nio.file Paths)))

(defn find-project-paths
  "Returns a vector of paths that are within this project."
  [classpath-vec cwd]
  (into []
        (comp
          (map (fn [path]
                 (-> path
                     (Paths/get (make-array String 0))
                     (.toAbsolutePath)
                     (.normalize))))
          (filter (fn [path]
                    (str/starts-with? path cwd)))
          (map str))
        classpath-vec))

(defn- namespaces-in-project
  []
  (let [project-paths (-> (shell/classpath-string-from-clj)
                          (util/parse-classpath-string)
                          (find-project-paths fs/cwd))]
    (set (ns.find/find-namespaces (map io/file project-paths)))))

(defn aot
  {:clake/cli-specs [["-t" "--target-path PATH" "Path for compilation."
                      :default "target"]
                     ["-a" "--aot NAMESPACES" "Comma separated list of namespaces to compile."
                      :parse-fn (fn [s] (map symbol (str/split s #",")))]
                     [nil "--all" "Set to true to AOT all project files."]]}
  [{:keys [aot all target-path]}]
  (let [compile-path (io/file target-path "classes")
        namespaces-to-compile (set (if all (namespaces-in-project) aot))]
    (if-not (empty? namespaces-to-compile)
      (do
        ;; ensure our compile path is created to avoid CompilerException
        (.mkdirs compile-path)
        (binding [*compile-path* (str compile-path)]
          (doseq [ns-sym namespaces-to-compile]
            (log/info "Compiling" ns-sym "...")
            (compile ns-sym))))
      (task/exit false "No namespaces to compile. Set either --aot or --all."))))