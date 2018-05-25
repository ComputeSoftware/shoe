(ns clake-cli.core
  (:require
    ["process" :as process]
    [clojure.string :as str]
    [clojure.set :as sets]
    [cljs.tools.reader.edn :as edn]
    [cljs.tools.cli :as cli]
    [clake-tasks.specs :as specs]
    [clake-tasks.api :as api]
    [clake-tasks.log :as log]
    [clake-cli.io :as io]
    [clake-cli.macros :as macros]
    [clake-tasks.shell :as shell]))

(def config-name "clake.edn")
(def jvm-entrypoint-ns 'clake-tasks.script.entrypoint)
(def clake-jvm-deps-alias :clake-jvm-deps)
(macros/def-env-var circle-ci-sha1 "CIRCLE_SHA1")

(def cli-options
  ;; An option with a required argument
  [["-h" "--help"]
   ["-v" "--version" "Print Clake version."]
   ["-c" "--config-files PATH" "Comma separated list of paths to deps.edn to include."
    :default [:install :user :project]
    :parse-fn (fn [comma-list]
                (mapv (fn [path]
                        (if (str/starts-with? path ":")
                          (keyword (subs path 1))
                          path))
                      (str/split comma-list ",")))
    :validate [(fn [paths] (sets/subset? (into #{} (filter keyword?) paths) #{:install :user :project}))
               "Keyword paths must be one of #{:install :user :project}."
               (fn [paths]
                 (every? io/exists? (filter string? paths)))
               "All paths must exist."]]
   ["-a" "--clj-args ARGS" ""]
   [nil "--tasks-sha SHA" "The git SHA to use for the clake-tasks dependency."]])

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

(defn with-clake-deps
  "Add the deps Clake needs to start tasks to the alias `clake-jvm-deps-alias`."
  [deps-edn {:keys [tasks-sha]} target-path]
  (assoc-in deps-edn
            [:aliases clake-jvm-deps-alias]
            {:extra-deps  {'clake-tasks (if-let [sha (or tasks-sha circle-ci-sha1)]
                                          {:git/url   "git@github.com:ComputeSoftware/clake.git"
                                           :sha       sha
                                           :deps/root "tasks"}
                                          {:local/root "../tasks"})}
             :extra-paths [(io/resolve (str target-path "/classes"))]}))

(defn resolve-deps-edn-paths
  "Returns a vector of filesystem paths given a list of `deps-edn-paths` that
  may contain keywords."
  [deps-edn-paths]
  (into []
        (filter some?)
        (if (empty? (filter keyword? deps-edn-paths))
          deps-edn-paths
          (let [[install-deps user-deps project-deps] (-> {:command :describe
                                                           :as      :edn}
                                                          (shell/clojure-deps-command)
                                                          :out :config-files)]
            (map (fn [path]
                   (if (keyword? path)
                     (case path
                       :install install-deps
                       :user user-deps
                       :project project-deps)
                     path)) deps-edn-paths)))))

(defn full-deps-edn
  "Returns the fully merged deps.edn as EDN."
  [deps-edn-paths]
  (:out
    (shell/clojure-deps-command {:deps-edn  '{:deps {org.clojure/tools.deps.alpha {:mvn/version "0.5.435"}}}
                                 :eval-code ['(require '[clojure.tools.deps.alpha.reader :as reader])
                                             (list 'reader/read-deps deps-edn-paths)]
                                 :as        :edn})))

(defn aliases-from-config
  "Returns a set of all aliases set in the `:task-opts` key in the config for the
  given `task-cli-args`."
  [task-cli-args config]
  (let [tasks-in-args (into #{}
                            (map symbol)
                            (:arguments (cli/parse-opts task-cli-args [])))]
    (reduce-kv (fn [all-aliases task-name {:keys [aliases]}]
                 (if (contains? tasks-in-args task-name)
                   (vec (concat all-aliases aliases))
                   all-aliases)) [] (:task-opts config))))

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
      (api/exit true (usage-text summary))
      (:version options)
      (api/exit true (cli-version-string))
      errors
      (api/exit false (error-msg errors))
      (nil? (first arguments))
      (api/exit true (usage-text summary))
      :else {:clake/task-cli-args arguments
             :clake/cli-opts      options})))

(defn run-task-command
  [{:clake/keys [cli-opts] :as context}]
  (let [config (let [c (load-config config-name)]
                 (cond-> c
                         (not (:target-path c)) (assoc :target-path "target")))
        target-path (:target-path config)
        deps-edn-paths (resolve-deps-edn-paths (:deps-edn-paths cli-opts))
        deps-edn (with-clake-deps (full-deps-edn deps-edn-paths) cli-options target-path)
        context (assoc context :clake/config config
                               :clake/deps-edn deps-edn)
        aliases (conj (aliases-from-config (:clake/task-cli-args context) config)
                      clake-jvm-deps-alias)]
    {:deps-edn deps-edn
     :aliases  aliases
     :main     jvm-entrypoint-ns
     :args     context
     :cmd-opts {:stdio ["ignore" "inherit" "inherit"]}}))

(defn run-task
  [context]
  (let [clojure-cmd-opts (run-task-command context)
        {:keys [exit out err] :as r} (shell/clojure-deps-command clojure-cmd-opts)]
    (api/exit exit (if (shell/status-success? r) out err))))

(defn clj-installed?
  []
  (try
    (some? (shell/clojure-command ["--help"]))
    (catch js/Error _ false)))

(defn execute
  [args]
  (if (clj-installed?)
    (let [context (validate-args args)]
      (if (api/exit? context)
        context
        (run-task context)))
    (api/exit false (str "Clojure CLI tools are not installed or globally accessible.\n"
                         "Install guide:\n"
                         "  https://clojure.org/guides/getting_started#_clojure_installer_and_cli_tools"))))

(defn exit
  [status msg]
  (when msg
    (if (= status 0)
      (log/info msg)
      (log/error msg)))
  (process/exit status))

(defn -main
  [& args]
  (let [{:clake-exit/keys [status message]} (execute args)]
    (exit status message)))