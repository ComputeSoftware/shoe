(ns clake-cli.core
  (:require
    ["process" :as process]
    [clojure.string :as str]
    [clojure.set :as sets]
    [cljs.tools.reader.edn :as edn]
    [cljs.tools.cli :as cli]
    [clake-common.log :as log]
    [clake-cli.io :as io]
    [clake-cli.macros :as macros]
    [clake-common.shell :as shell]))

(def config-name "clake.edn")
(def jvm-entrypoint-ns 'clake-tasks.script.entrypoint)
(def clake-jvm-deps-alias :clake-jvm-deps)
(macros/def-env-var circle-ci-sha1 "CIRCLE_SHA1")

(def cli-options
  ;; An option with a required argument
  [["-h" "--help"]
   ["-v" "--version" "Print Clake version."]
   ["-A" "--aliases VALS" "Comma separated list of aliases to use."
    :parse-fn (fn [vals-str]
                (vec (str/split vals-str #",")))]
   [nil "--sha STR" "Custom Git SHA to run Clake with."]
   [nil "--local PATH" "Path to use for a local dependency of clake-common."
    :validate [(fn [x] (io/exists? x))]]])

(defn usage-text
  [options-summary]
  (->> ["Usage: clake [clake-opt*] [task] [arg*]"
        ""
        "Clake is a build tool for Clojure(Script). It uses the Clojure CLI to "
        "start a JVM."
        ""
        "Options:"
        options-summary
        ""]
       ;; it'd be nice to show a list of available tasks but that is only possible
       ;; if we start a JVM.
       (str/join "\n")))

(defn error-msg
  [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn load-config
  [config-path]
  (when (io/exists? config-path)
    (io/slurp-edn config-path)))

(defn cli-version-string
  []
  (str "Version SHA: " (or circle-ci-sha1 "local") "\n"
       "NPM: " (let [r (shell/spawn-sync "npm" ["info" "clake-cli" "version"])]
                 (if (shell/status-success? r) (:out r) "n/a"))))

(defn parse-cli-opts
  [args]
  (cli/parse-opts args cli-options :in-order true))

(defn validate-args
  [args]
  (let [{:keys [options arguments errors summary]} (parse-cli-opts args)]
    (cond
      (:help options)
      (shell/exit true (usage-text summary))
      (:version options)
      (shell/exit true (cli-version-string))
      errors
      (shell/exit false (error-msg errors))
      (nil? (first arguments))
      (shell/exit true (usage-text summary))
      :else {:clake/task-cli-args arguments
             :clake/cli-opts      options})))

(defn run-entrypoint-command
  [aliases args]
  {:deps-edn {:aliases {clake-jvm-deps-alias
                        {:extra-deps {'clake-common {:local/root "../tasks"}}}}}
   :aliases  (conj aliases clake-jvm-deps-alias)
   :main     "clake-common.script.entrypoint"
   :args     args})

(defn resolve-clake-common-coordinate
  [{:keys [local sha]}]
  (let [sha (or sha circle-ci-sha1)]
    (cond
      local {:local/root local}
      sha {:git/url   "https://github.com/ComputeSoftware/clake"
           :sha       sha
           :deps/root "common"}
      :else nil)))

(defn run-entrypoint
  [{:clake/keys [task-cli-args cli-opts]}]
  (if-let [clake-common-coord (resolve-clake-common-coordinate cli-opts)]
    (let [r (shell/clojure-deps-command
              {:deps-edn {:aliases {clake-jvm-deps-alias {:extra-deps {'clake-common clake-common-coord}}}}
               :aliases  (conj (or (:aliases cli-opts) []) clake-jvm-deps-alias)
               :main     "clake-common.script.entrypoint"
               :args     {:extra-deps {'clake-common clake-common-coord}
                          :args       task-cli-args
                          :aliases    (:aliases cli-opts)}
               :cmd-opts {:stdio ["ignore" "inherit" "inherit"]}})]
      (shell/exit (:exit r)))
    (shell/exit false "Could not resolve clake-common.")))

(defn clj-installed?
  []
  (try
    (some? (shell/clojure-command ["--help"]))
    (catch js/Error _ false)))

(defn execute
  [args]
  (if (clj-installed?)
    (let [result (validate-args args)]
      (if-not (shell/exit? result)
        (run-entrypoint result)
        result))
    (shell/exit false (str "Clojure CLI tools are not installed or globally accessible.\n"
                           "Install guide:\n"
                           "  https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools"))))

(defn -main
  [& args]
  (shell/system-exit (execute args)))