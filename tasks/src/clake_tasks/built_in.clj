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
    [clake-tasks.util :as util]
    [clake-tasks.log :as log]
    [clake-tasks.tasks.uberjar :as uberjar-impl]
    [clake-tasks.tasks.project-clj :as project-cli-impl])
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
    (log/info (format "nREPL server started on port %s" selected-port))
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
  (let [namespaces (util/namespaces-in-project deps-edn aliases)
        ;; we need to make sure that all namespaces have been loaded
        _ (apply require namespaces)
        {:keys [fail]} (apply clj-test/run-tests namespaces)]
    (when (not= 0 fail)
      (api/exit false))))

(api/deftask uberjar
  ""
  {:clake/cli-specs []}
  [opts context]
  (uberjar-impl/uberjar opts context))

(api/deftask project-clj
  "Generate a project.clj from your deps.edn using lein-tools-deps."
  {:clake/cli-specs []}
  [opts ctx]
  (project-cli-impl/project-clj opts ctx))