(ns clake-tasks.archive
  (:require
    [hara.io.file :as fs]
    [hara.io.archive :as archive]
    [clake-common.log :as log]))

(defn archive
  "Creates an archive."
  {:clake/cli-specs [["-i" "--in PATH" "Input directory"
                      :validate-fn (fn [path]
                                     (fs/exists? path))]
                     ["-o" "--out PATH" "Output path for the archive."]]}
  [{:keys [in out]}]
  (log/info "Creating" out "...")
  (when (fs/exists? out)
    (log/info out "exists. Overwriting."))
  (fs/delete out)
  (archive/archive out in))