(ns cerber.oauth2.scopes
  (:require [clojure.string :as str]))

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

(defn normalize-scopes
  "Normalizes set of scopes by removing duplicates and overlapping entries."

  [scopes]
  (->> scopes
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
