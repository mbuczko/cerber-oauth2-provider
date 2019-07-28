(ns cerber.test-runner
  (:require [midje.repl :refer [load-facts]]))


(defn -main []
  (load-facts))
