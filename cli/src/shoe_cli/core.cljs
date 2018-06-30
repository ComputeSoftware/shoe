(ns shoe-cli.core
  (:require
    ["process" :as process]
    [clojure.string :as str]
    [clojure.set :as sets]
    [cljs.tools.reader.edn :as edn]
    [cljs.tools.cli :as cli]
    [shoe-common.log :as log]
    [shoe-cli.io :as io]
    [shoe-cli.macros :as macros]
    [shoe-common.shell :as shell]
    ;[shoe-common.script.built-in-tasks :as built-in]
    ))

(def config-name "shoe.edn")
(def jvm-entrypoint-ns 'shoe-tasks.script.entrypoint)
(macros/def-env-var circle-ci-sha1 "CIRCLE_SHA1")
(macros/def-built-in-task-dirs built-in-dirs)

(def cli-options
  ;; An option with a required argument
  [["-h" "--help"]
   ["-v" "--version" "Print shoe version."]
   ["-A" "--aliases VALS" "Comma separated list of aliases to use."
    :parse-fn (fn [vals-str]
                (vec (str/split vals-str #",")))]
   [nil "--sha STR" "Custom Git SHA to run shoe with."]
   [nil "--local PATH" "Path to the shoe repository on disk for local dependencies."
    :validate [(fn [x] (io/exists? x))]]])

(defn usage-text
  [options-summary]
  (->> ["Usage: shoe [shoe-opt*] [task] [arg*]"
        ""
        "shoe is a build tool for Clojure(Script). It uses the Clojure CLI to "
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
       "NPM: " (let [r (shell/spawn-sync "npm" ["info" "shoe-cli" "version"])]
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
      :else {:shoe/task-cli-args arguments
             :shoe/cli-opts      options})))

(defn resolve-shoe-common-coordinate
  [{:keys [local sha]}]
  (let [sha (or sha circle-ci-sha1)]
    (cond
      local {:local/root (str local "/common")}
      sha {:git/url   "https://github.com/ComputeSoftware/shoe"
           :sha       sha
           :deps/root "common"}
      :else nil)))

(defn built-in-task-deps
  [{:keys [local sha]}]
  (reduce (fn [deps dir]
            (assoc deps (symbol (str "shoe.tasks/" dir))
                        (cond
                          local
                          {:local/root (str local "/tasks/" dir)}
                          (or sha circle-ci-sha1)
                          {:git/url   "https://github.com/ComputeSoftware/shoe"
                           :sha       (or sha circle-ci-sha1)
                           :deps/root (str "tasks/" dir)})))
          {} built-in-dirs))

(defn run-entrypoint
  [{:shoe/keys [task-cli-args cli-opts]}]
  (if-let [shoe-common-coord (resolve-shoe-common-coordinate cli-opts)]
    (let [r (shell/clojure-deps-command
              {:deps-edn {:deps (merge {'shoe/common shoe-common-coord}
                                       (built-in-task-deps cli-opts))}
               :aliases  (:aliases cli-opts)
               :main     "shoe-common.script.entrypoint"
               :args     {:extra-deps {'shoe/common shoe-common-coord}
                          :args       task-cli-args
                          :aliases    (:aliases cli-opts)}
               :cmd-opts {:stdio ["inherit" "inherit" "inherit"]}})]
      (shell/exit (:exit r)))
    (shell/exit false "Could not resolve shoe-common.")))

(defn clj-installed?
  []
  (try
    (shell/status-success? (shell/clojure-command ["--help"]))
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