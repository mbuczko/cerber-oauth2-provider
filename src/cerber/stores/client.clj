(ns cerber.stores.client
  (:require [mount.core :refer [defstate]]
            [cerber
             [config :refer [app-config]]
             [db :as db]
             [store :refer :all]]
            [clojure.string :as str]
            [failjure.core :as f]
            [cerber.error :as error]
            [cerber.stores.token :as token]
            [cerber.helpers :as helpers]))

(declare init-clients)

(defrecord Client [id secret info redirects grants scopes approved? enabled? created-at modified-at activated-at blocked-at])

(defrecord SqlClientStore [normalizer]
  Store
  (fetch-one [this [client-id]]
    (-> (db/find-client {:id client-id})
        first
        normalizer))
  (revoke-one! [this [client-id]]
    (db/delete-client {:id client-id}))
  (store! [this k client]
    (when (= 1 (db/insert-client client)) client))
  (modify! [this k client]
    (if (:enabled? client)
      (db/enable-client client)
      (db/disable-client client)))
  (purge! [this]
    (db/clear-clients)))

(defn normalize [client]
  (when-let [{:keys [id secret info approved scopes grants redirects enabled created_at modified_at activated_at blocked_at]} client]
    (map->Client {:id id
                  :secret secret
                  :info info
                  :approved? approved
                  :enabled? enabled
                  :scopes (helpers/str->coll [] scopes)
                  :grants (helpers/str->coll [] grants)
                  :redirects (helpers/str->coll [] redirects)
                  :created-at created_at
                  :modified-at modified_at
                  :activated-at activated_at
                  :blocked-at blocked_at})))

(defmulti create-client-store identity)

(defmacro with-client-store
  "Changes default binding to default client store."
  [store & body]
  `(binding [*client-store* ~store] ~@body))

(defstate ^:dynamic *client-store*
  :start (create-client-store (-> app-config :clients :store)))

(defstate defined-clients
  :start (init-clients (-> app-config :clients :defined)))

(defmethod create-client-store :in-memory [_]
  (->MemoryStore "clients" (atom {})))

(defmethod create-client-store :redis [_]
  (->RedisStore "clients" (:redis-spec app-config)))

(defmethod create-client-store :sql [_]
  (->SqlClientStore normalize))

(defn validate-uri
  "Returns java.net.URL instance of given uri or failure info in case of error."
  [uri]
  (if (empty? uri)
    (error/internal-error "redirect-uri cannot be empty")
    (if (or (>= (.indexOf uri "#") 0)
            (>= (.indexOf uri " ") 0)
            (>= (.indexOf uri "..") 0))
      (error/internal-error "Illegal characters in redirect URI")
      (try
        (java.net.URL. uri)
        (catch Exception e (error/internal-error (.getMessage e)))))))

(defn validate-redirects
  "Goes through all redirects and returns list of validation failures."
  [redirects]
  (filter f/failed? (map validate-uri redirects)))

(defn revoke-client
  "Revokes previously generated client and all tokens generated to this client so far."
  [client]
  (let [id (:id client)]
    (revoke-one! *client-store* [id])
    (token/revoke-client-tokens client)))

(defn enable-client
  "Enabled client. Returns true if client has been enabled successfully or false otherwise."
  [client]
  (= 1 (modify! *client-store* [:id] (assoc client :enabled? true :activated-at (helpers/now)))))

(defn disable-client
  "Disables client. Returns true if client has been disabled successfully or false otherwise."
  [client]
  (token/revoke-client-tokens client)
  (= 1 (modify! *client-store* [:id] (assoc client :enabled? false :blocked-at (helpers/now)))))

(defn create-client
  "Creates new client"
  [info redirects grants scopes approved? & [id secret]]
  (let [result (validate-redirects redirects)
        client {:id (or id (helpers/generate-secret))
                :secret (or secret (helpers/generate-secret))
                :info info
                :approved? approved?
                :enabled? true
                :scopes (helpers/coll->str scopes)
                :grants (helpers/coll->str grants)
                :redirects (helpers/coll->str redirects)
                :activated-at (helpers/now)
                :created-at (helpers/now)}]

    (if (empty? result)
      (if (store! *client-store* [:id] client)
        (map->Client (assoc client
                            :scopes (or scopes [])
                            :grants (or grants [])
                            :redirects (or redirects [])))
        (error/internal-error "Cannot store client"))
      (first result))))

(defn find-client
  "Returns client with given id, if found or nil otherwise."
  [client-id]
  (if-let [found (and client-id (fetch-one *client-store* [client-id]))]
    (let [{:keys [approved scopes grants redirects]} found]
      (map->Client found))))

(defn purge-clients
  []
  "Removes clients from store. Used for tests only."
  (purge! *client-store*))

(defn init-clients
  "Initializes configured clients."
  [clients]
  (f/try*
   (reduce (fn [reduced {:keys [id secret info redirects grants scopes approved?]}]
             (conj reduced (create-client info redirects grants scopes approved? id secret)))
           {}
           clients)))

(defn grant-allowed? [client grant]
  (let [grants (:grants client)]
    (or (empty? grants)
        (.contains grants grant))))

(defn redirect-uri-valid? [client redirect-uri]
  (.contains (:redirects client) redirect-uri))

(defn scopes-valid?
  "Checks whether given scopes are valid ones assigned to client."
  [client scopes]
  (let [client-scopes (:scopes client)]
    (or (empty? scopes)
        (every? #(.contains client-scopes %) scopes))))
