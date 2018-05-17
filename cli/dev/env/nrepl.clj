(ns env.nrepl
  (:require
    [clojure.tools.nrepl.server :as nrepl-server]))

(nrepl-server/start-server :port 8889)