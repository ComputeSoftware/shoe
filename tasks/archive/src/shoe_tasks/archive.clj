(ns shoe-tasks.archive
  (:require
    [shoe-common.fs :as fs]
    [shoe-common.log :as log]))

(defn archive
  "Creates an archive."
  {:shoe/cli-specs [["-i" "--in PATH" "Input directory"
                      :validate-fn (fn [path]
                                     (fs/exists? path))]
                     ["-o" "--out PATH" "Output path for the archive."]]}
  [{:keys [in out]}]
  (log/info "Creating" out "...")
  (when (fs/exists? out)
    (log/info out "exists. Overwriting."))
  (fs/delete out)
  (fs/archive in out))