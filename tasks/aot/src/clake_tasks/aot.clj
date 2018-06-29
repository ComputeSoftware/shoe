(ns clake-tasks.aot
  (:require
    [clojure.java.io :as io]
    [clojure.string :as str]
    [clojure.tools.namespace.find :as ns.find]
    [clake-common.log :as log]
    [clake-common.shell :as shell]
    [clake-common.task :as task])
  (:import (java.nio.file Paths)))

(defn get-cwd
  []
  (System/getProperty "user.dir"))

(defn classpath-string-from-clj
  []
  ;; TODO: include aliases here
  (let [r (shell/clojure-deps-command {:command :path})]
    (when (shell/status-success? r)
      (str/trim-newline (:out r)))))

(defn parse-classpath-string
  "Returns a vector of classpath paths."
  [cp-string]
  (str/split cp-string (re-pattern (System/getProperty "path.separator"))))

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
  (let [project-paths (-> (classpath-string-from-clj)
                          (parse-classpath-string)
                          (find-project-paths (get-cwd)))]
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