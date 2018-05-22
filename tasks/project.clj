(defproject clake-tasks "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  ; waiting on https://github.com/RickMoynihan/lein-tools-deps/pull/31
  ;:plugins [[lein-tools-deps "0.3.0-SNAPSHOT"]]
  ;:tools/deps [:system :home :project]
  :dependencies [[org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.nrepl "0.2.13"]
                 [org.clojure/tools.cli "0.3.7"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [pjstadig/humane-test-output "0.8.3"]
                 [zcaudate/hara.io.file "2.8.2"]
                 [zcaudate/hara.io.archive "2.8.2"]]
  :main clake-tasks.script.entrypoint
  :aot [clake-tasks.script.entrypoint])
