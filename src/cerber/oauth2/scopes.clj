(ns cerber.oauth2.scopes
  (:require [cerber.helpers :refer [str->vec]]
            [clojure.string :as str]))

(defn distinct-scope
  "Returns nil if scopes contain given scope itself or any of its parents.
   Returns scope otherwise."

  [scopes scope]
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
       (str->vec)
       (sort-by #(count (re-seq #":" %)))
       (reduce (fn [reduced scope]
                 (if-let [s (distinct-scope reduced scope)]
                   (conj reduced s)
                   reduced))
               #{})))

(defn allowed-scopes?
  "Checks whether all given scopes appear in a set of allowed-scopes."
  [scopes allowed-scopes]
  (let [filtered (->> scopes
                      (map #(contains? allowed-scopes %))
                      (filter true?))]

    (= (count filtered)
       (count scopes))))
