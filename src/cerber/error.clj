(ns cerber.error
  (:require [failjure.core :as f]
            [cerber.config :refer [app-config]]))

(defrecord HttpError [error message code])

(extend-protocol f/HasFailed
  HttpError
  (message [this] (:message this))
  (failed? [this] true))

(def invalid-request
  (map->HttpError {:error "invalid_request" :message "Invalid request" :code 400}))

(def invalid-scope
  (map->HttpError {:error "invalid_scope" :message "Invalid scope" :code 400}))

(def invalid-state
  (map->HttpError {:error "invalid_state" :message "Invalid state. Only alphanumeric characters are allowed." :code 400}))

(def invalid-grant
  (map->HttpError {:error "invalid_grant" :message "Invalid grant" :code 400}))

(def invalid-client
  (map->HttpError {:error "invalid_client" :message "Invalid client" :code 400}))

(def invalid-authcode
  (map->HttpError {:error "invalid_authcode" :message "Invalid code or redirect URI" :code 400}))

(def invalid-token
  (map->HttpError {:error "invalid_token" :message "Invalid refresh token" :code 400}))

(def invalid-redirect-uri
  (map->HttpError {:error "invalid_redirect_uri" :message "Invalid redirect URI" :code 400}))

(def unapproved
  (map->HttpError {:error "unapproved" :message "Authorization not approved" :code 403}))

(def unauthorized
  (map->HttpError {:error "unauthorized" :message "Authorization failed" :code 401}))

(def forbidden
  (map->HttpError {:error "forbidden" :message "No permission to the resource" :code 403}))

(def unsupported-response-type
  (map->HttpError {:error "unsupported_response_type" :message "Unsupported response type" :code 400}))

(def unsupported-grant-type
  (map->HttpError {:error "unsupported_grant_type" :message "Unsupported grant type" :code 400}))

(def server-error
  (map->HttpError {:error "server_error" :message "Invalid request" :code 500}))

(defn internal-error [message]
  (map->HttpError {:error "server_error" :message message :code 500}))

(defn error->json [http-error state]
  (let [{:keys [code error message]} http-error]
    (if (or (= code 401) (= code 403))
      {:status code
       :headers {"WWW-Authenticate" (str "Bearer realm=\"" (get-in app-config [:cerber :realm])
                                         "\",error=\"" error
                                         "\",error_description=" message "\"")}}
      {:status (or code 500)
       :body (-> {:error (or error "server_error")
                  :error_description message}
                 (cond-> state (assoc :state state)))})))
