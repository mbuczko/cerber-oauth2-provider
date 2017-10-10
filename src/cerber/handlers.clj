(ns cerber.handlers
  (:require [cerber
             [error :as error]
             [form :as form]
             [middleware :refer [session-store]]]
            [cerber.oauth2
             [authorization :as auth]
             [context :as ctx]]
            [ring.middleware
             [anti-forgery :refer [wrap-anti-forgery]]
             [format :refer [wrap-restful-format]]
             [session :refer [wrap-session]]]))

(def custom-store (session-store))

(defn wrap-errors [handler]
  (fn [req]
    (let [response (handler req), params (:params req)]
      (if-let [error (:error response)]
        (if (= (:code response) 302)
          (error/error->redirect response (:state params) (:redirect_uri params))
          (error/error->json response (:state params) (:headers req) (:uri req)))
        response))))

(defn wrap-authorization [handler]
  (fn [req]
    (let [result (or (ctx/user-logged? req)
                     (ctx/bearer-valid? req))]

      (if (:error result)
        result
        (handler result)))))

(defn wrap-authorized [handler]
  (-> handler
      (wrap-authorization)
      (wrap-errors)
      (wrap-session {:store custom-store})
      (wrap-restful-format :formats [:json-kw])))

(defn login-form-handler [req]
  (-> form/render-login-form
      (wrap-anti-forgery)
      (wrap-session {:store custom-store})))

(defn login-submit-handler [req]
  (-> form/handle-login-submit
      (wrap-anti-forgery)
      (wrap-session {:store custom-store})))

(defn authorization-handler [req]
  (-> auth/authorize!
      (wrap-errors)
      (wrap-session {:store custom-store})
      (wrap-restful-format :formats [:json-kw])))

(defn client-approve-handler [req]
  (-> auth/approve!
      (wrap-errors)
      (wrap-anti-forgery)
      (wrap-session {:store custom-store})
      (wrap-restful-format :formats [:json-kw])))

(defn client-refuse-handler [req]
  (-> auth/refuse!
      (wrap-errors)
      (wrap-restful-format :formats [:json-kw])))

(defn token-handler [req]
  (-> auth/issue-token!
      (wrap-errors)
      (wrap-restful-format :formats [:json-kw])))
