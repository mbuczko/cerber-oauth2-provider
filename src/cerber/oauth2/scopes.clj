(ns cerber.oauth2.scopes
  (:require [cerber.config :refer [app-config]]
            [cerber.helpers :refer [str->coll]]
            [clojure.string :as str]))

(defn- distinct-scope
  "Returns falsey if scopes contain given scope or any of its parents.
  Returns scope otherwise."

  [scopes ^String scope]
  (let [v (.split scope ":")]
    (loop [s v]
      (if (empty? s)
        scope
        (when-not (contains? scopes (str/join ":" s))
          (recur (drop-last s)))))))

(defn normalize-scope
  "Normalizes scope string by removing duplicated and overlapping scopes."

  [scope]
  (->> scope
       (str->coll [])
       (sort-by #(count (re-seq #":" %)))
       (reduce (fn [reduced scope]
                 (if-let [s (distinct-scope reduced scope)]
                   (conj reduced s)
                   reduced))
               #{})))
