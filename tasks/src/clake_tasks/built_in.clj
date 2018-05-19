(ns clake-tasks.built-in
  (:refer-clojure :exclude [test])
  (:require
    [clojure.main :as main]
    [clojure.test :as clj-test]
    [clojure.pprint :as pprint]
    [clojure.java.io :as io]
    [clojure.tools.nrepl.server :as nrepl-server]
    [clake-tasks.api :as api]
    [pjstadig.humane-test-output :as humane-test]
    [clake-tasks.util :as util])
  (:import (java.nio.file Files)))

(api/deftask nrepl
  "Task that starts an nREPL server.

  Note: This is a blocking task."
  {:clake/cli-specs   [["-p" "--port PORT" "Port to start nREPL server on."
                        :default 0
                        :parse-fn #(Integer/parseInt %)]
                       ["-l" "--lein-port" "Spit the port to .nrepl-port."]]
   :clake/shutdown-fn (fn []
                        (Files/deleteIfExists (.toPath (io/file ".nrepl-port"))))}
  [{:keys [port lein-port] :as task-opts} _]
  (let [#_#_repl (main/repl)
        server (nrepl-server/start-server :port port)
        selected-port (:port server)]
    (println (format "nREPL server started on port %s" selected-port))
    (when lein-port
      (spit ".nrepl-port" selected-port))
    @(promise)))

(api/deftask print-context
  "Pretty prints the current context."
  {:clake/cli-specs []}
  [_ context]
  (pprint/pprint context))

(api/deftask test
  "Run the project's tests."
  {:clake/cli-specs []}
  [{:keys [aliases]} {:clake/keys [deps-edn]}]
  (humane-test/activate!)
  (let [namespaces (util/namespaces-in-project deps-edn aliases)]
    ;; make sure that all namespaces have been loaded
    (apply require namespaces)
    ;; run da tests
    (apply clj-test/run-tests namespaces)
    ))

(api/deftask aot
  "Perform AOT compilation of Clojure namespaces."
  {:clake/cli-specs [["-a" "--all" "Compile all namespaces"]
                     ["-n" "--namespaces #{sym}" "A set of namespaces to compile."]]}
  [{:keys [all namespaces]} _]
  (println "aot"))

(api/deftask project-clj
  "Generate a project.clj from your deps.edn"
  {:clake/cli-specs []}
  [_ _]
  (println "aot2"))