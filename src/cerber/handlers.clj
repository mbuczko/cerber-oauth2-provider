(ns cerber.handlers
  (:require [cerber
             [error :as error]
             [form :as form]]
            [cerber.oauth2
             [authorization :as auth]
             [context :as ctx]]
            [cerber.stores.session :refer [create-session extend-session find-session revoke-session update-session]]
            [ring.middleware
             [anti-forgery :refer [wrap-anti-forgery]]
             [format :refer [wrap-restful-format]]
             [session :refer [wrap-session]]]
            [ring.middleware.session.store :refer [SessionStore]]
            [failjure.core :as f]))

(deftype RingCustomizedStore []
  SessionStore
  (read-session [_ key]
    (when-let [session (find-session key)]
      (:content (extend-session session))))
  (write-session [_ key data]
    (:sid
     (if key
       (when-let [session (find-session key)]
         (update-session (assoc session :content data)))
       (create-session data))))
  (delete-session [_ key]
    (revoke-session (find-session key))
    nil))

(defonce session-store (RingCustomizedStore.))

(defn wrap-errors [handler]
  (fn [req]
    (let [response (handler req)]
      (if (:error response)
        (if (= (:code response) 302)
          (error/error->redirect response req)
          (error/error->edn response req))
        response))))

(defn wrap-context [handler redirect-on-error?]
  (fn [req]
    (let [result (or (and (-> req :session :login)
                          (ctx/user-authenticated? req))
                     (ctx/bearer-valid? req))]
      (if (f/failed? result)
        (if redirect-on-error?
          result
          (handler req))
        (handler result)))))

(defn wrap-maybe-authorized [handler]
  (-> handler
      (wrap-context false)
      (wrap-session {:store session-store})))

(defn wrap-authorized [handler]
  (-> handler
      (wrap-context true)
      (wrap-errors)
      (wrap-session {:store session-store})))

(defn login-form-handler [req]
  (-> form/render-login-form
      (wrap-anti-forgery)
      (wrap-session {:store session-store})))

(defn login-submit-handler [req]
  (-> form/handle-login-submit
      (wrap-anti-forgery)
      (wrap-session {:store session-store})))

(defn logout-handler [req]
  (-> auth/unauthorize!
      (wrap-context false)
      (wrap-errors)
      (wrap-session {:store session-store})))

(defn authorization-handler [req]
  (-> auth/authorize!
      (wrap-errors)
      (wrap-session {:store session-store})
      (wrap-restful-format :formats [:json-kw])))

(defn client-approve-handler [req]
  (-> auth/approve!
      (wrap-errors)
      (wrap-anti-forgery)
      (wrap-session {:store session-store})
      (wrap-restful-format :formats [:json-kw])))

(defn client-refuse-handler [req]
  (-> auth/refuse!
      (wrap-errors)
      (wrap-restful-format :formats [:json-kw])))

(defn token-handler [req]
  (-> auth/issue-token!
      (wrap-errors)
      (wrap-restful-format :formats [:json-kw])))
