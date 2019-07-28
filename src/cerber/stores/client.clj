(ns cerber.stores.client
  "Functions handling OAuth2 client storage."
  (:require [cerber.stores.token :as token]
            [cerber
             [db :as db]
             [error :as error]
             [helpers :as helpers]
             [mappers :as mappers]
             [store :refer [Store ->MemoryStore ->RedisStore fetch-one revoke-one! purge! store! modify!]]]
            [failjure.core :as f]))

(def client-store (atom :not-initialized))

(defrecord Client [id secret info redirects grants scopes approved? enabled? created-at modified-at activated-at blocked-at])

(defrecord SqlClientStore []
  Store
  (fetch-one [this [client-id]]
    (some-> (db/find-client {:id client-id})
            mappers/row->client))
  (revoke-one! [this [client-id]]
    (db/delete-client {:id client-id}))
  (store! [this k client]
    (= 1 (db/insert-client (-> client
                               (update :scopes helpers/coll->str)
                               (update :grants helpers/coll->str)
                               (update :redirects helpers/coll->str)))))
  (modify! [this k client]
    (if (:enabled? client)
      (db/enable-client client)
      (db/disable-client client)))
  (purge! [this]
    (db/clear-clients))
  (close! [this]
    ))

(defmulti create-client-store (fn [type config] type))

(defmethod create-client-store :in-memory [_ _]
  (->MemoryStore "clients" (atom {})))

(defmethod create-client-store :redis [_ redis-spec]
  (->RedisStore "clients" redis-spec))

(defmethod create-client-store :sql [_ db-conn]
  (when db-conn
    (db/bind-queries db-conn)
    (->SqlClientStore)))

(defn init-store
  "Initializes client store according to given type and configuration."

  [type config]
  (reset! client-store (create-client-store type config)))

(defn validate-uri
  "Returns java.net.URL instance of given uri or failure info in case of error."

  [uri]
  (if (empty? uri)
    (error/internal-error "redirect-uri cannot be empty")
    (if (or (>= (.indexOf uri "#") 0)
            (>= (.indexOf uri "..") 0)
            (.matches uri ".*\\s+.*"))
      (error/internal-error "Illegal characters in redirect URI")
      (try
        (java.net.URL. uri)
        (catch Exception e (error/internal-error (.getMessage e)))))))

(defn validate-redirects
  "Goes through all redirects and returns list of validation failures."

  [redirects]
  (filter f/failed? (map validate-uri redirects)))

(defn find-client
  "Returns a client with given id if any found or nil otherwise."

  [client-id]
  (when-let [found (and client-id (fetch-one @client-store [client-id]))]
    (map->Client found)))

(defn revoke-client
  "Revokes previously generated client and all tokens generated to this client so far."

  [client]
  (let [id (:id client)]
    (revoke-one! @client-store [id])
    (token/revoke-client-tokens client)))

(defn purge-clients
  "Removes clients from store."

  []
  (purge! @client-store))

(defn enable-client
  "Enables client. Returns true if client has been enabled successfully or false otherwise."

  [client]
  (= 1 (modify! @client-store [:id] (assoc client :enabled? true :activated-at (helpers/now)))))

(defn disable-client
  "Disables client. Returns true if client has been disabled successfully or false otherwise."

  [client]
  (token/revoke-client-tokens client)
  (= 1 (modify! @client-store [:id] (assoc client :enabled? false :blocked-at (helpers/now)))))

(defn create-client
  "Creates and returns a new client."

  [grants redirects {:keys [info scopes enabled? approved? id secret]}]
  (let [result (validate-redirects redirects)
        client {:id (or id (helpers/generate-secret))
                :secret (or secret (helpers/generate-secret))
                :info info
                :approved? (boolean approved?)
                :enabled? (boolean enabled?)
                :scopes scopes
                :grants grants
                :redirects redirects
                :activated-at (helpers/now)
                :created-at (helpers/now)}]

    (if (seq result)
      (error/internal-error (first result))
      (if (store! @client-store [:id] client)
        (map->Client client)
        (error/internal-error "Cannot store client")))))

(defn grant-allowed?
  [client grant]
  (when-let [grants (:grants client)]
    (.contains grants grant)))

(defn redirect-uri-valid?
  [client redirect-uri]
  (.contains (:redirects client) redirect-uri))

(defn scopes-valid?
  "Checks whether given scopes are valid ones assigned to client."

  [client scopes]
  (let [client-scopes (:scopes client)]
    (or (empty? scopes)
        (every? #(.contains client-scopes %) scopes))))
