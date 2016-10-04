(ns cerber.handlers
  (:require  [cerber.oauth2.authorization :as auth]
             [cerber
              [error :as error]
              [form :as form]
              [middleware :refer [session-store]]]
             [ring.middleware
              [anti-forgery :refer [wrap-anti-forgery]]
              [format :refer [wrap-restful-format]]
              [session :refer [wrap-session]]]))

(defn wrap-oauth-errors [handler]
  (fn [req]
    (let [response (handler req)]
      (if-let [error (:error response)]
        (error/error->json response (:state (:params req)))
        response))))

(defn login-form-handler [req]
  (-> form/render-login-form
      (wrap-anti-forgery)
      (wrap-session {:store (session-store)})))

(defn login-submit-handler [req]
  (-> form/handle-login-submit
      (wrap-anti-forgery)
      (wrap-session {:store (session-store)})))

(defn authorization-handler [req]
  (-> auth/authorize!
      (wrap-oauth-errors)
      (wrap-session {:store (session-store)})
      (wrap-restful-format :formats [:json-kw])))

(defn authorization-approve-handler [req]
  (-> auth/approve!
      (wrap-oauth-errors)
      (wrap-anti-forgery)
      (wrap-session {:store (session-store)})
      (wrap-restful-format :formats [:json-kw])))

(defn token-handler [req]
  (-> auth/issue-token!
      (wrap-oauth-errors)
      (wrap-restful-format :formats [:json-kw])))
