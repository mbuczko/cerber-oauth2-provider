(ns cerber.oauth2.authorization-test
  (:require [cerber
             [config :refer [app-config]]
             [handlers :as handlers]]
            [cerber.stores
             [client :as c]
             [user :as u]]
            [compojure.core :refer [defroutes GET POST]]
            [midje.sweet :refer :all]
            [cerber.oauth2.common :refer :all]
            [peridot.core :refer :all]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]))

(defonce client (c/create-client "http://foo.com" ["http://foo.com/callback"] ["photo:read"]  nil ["moderator"] false))

(defroutes oauth-routes
  ;; (GET  "/authorize" [] handlers/authorization-handler)
  ;; (POST "/authorize" [] handlers/authorization-approve-handler)
  ;; (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))

(fact "enabled user with valid password is redirected to langing page when successfully logged in"

      ;; given
      (u/create-user {:login "nioh"} "alamakota")

      ;; when
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request "/login") ;; get csrf
                      (request-secured "/login"
                                       :request-method :post
                                       :params {:username "nioh"
                                                :password "alamakota"}))]
        ;; then
        (get-in state [:response :status]) => 302
        (get-in state [:response :headers "Location"]) => (get-in app-config [:cerber :landing-url])))
