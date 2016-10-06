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

(def redirect-uri "http://localhost")
(def scope "photo:read")
(def state "123ABC")

(defroutes oauth-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/authorize" [] handlers/authorization-approve-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))

(defonce client (c/create-client "http://foo.com" [redirect-uri] [scope]  nil ["moderator"] false))

(fact "enabled user with valid password is redirected to langing page when successfully logged in"
      (u/purge-users)

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

(fact "Wrong credentials redirect again to login page with failure info displayed."
      (u/purge-users)

      ;; given
      (u/create-user {:login "nioh"} "alamakota")

      ;; when
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request "/login")
                      (request-secured "/login"
                                       :request-method :post
                                       :params {:username "nioh"
                                                :password ""}))]

        ;; login page should be returned
        (get-in state [:response :status]) => 200
        (get-in state [:response :body]) => (contains "failed")))

(fact "Inactive user is not able to log in"
      (u/purge-users)

      ;; given
      (u/create-user {:login "nioh" :enabled false} "alamakota")

      ;; when
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request "/login")
                      (request-secured "/login"
                                       :request-method :post
                                       :params {:username "nioh"
                                                :password "alamakota"}))]

        ;; then
        (get-in state [:response :status]) => 200
        (get-in state [:response :body]) => (contains "failed")))

(fact "user may obtain his token (wired with specific oauth client) in grant_type=authorization code scenario"
      (u/purge-users)

      ;; given
      (u/create-user {:login "nioh" :enabled true} "alamakota")

      ;; when
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request (str "/authorize?response_type=code"
                                    "&client_id=" (:id client)
                                    "&scope=" scope
                                    "&state=" state
                                    "&redirect_uri=" redirect-uri))

                      ;; login window
                      (follow-redirect)
                      (request-secured "/login"
                                       :request-method :post
                                       :params {:username "nioh"
                                                :password "alamakota"})

                      ;; authorization prompt
                      (follow-redirect)
                      (request-secured "/authorize"
                                       :request-method :post
                                       :params {:client_id (:id client)
                                                :response_type "code"
                                                :redirect_uri redirect-uri})

                      ;; having access code received - final request for acess-token
                      (header "Authorization" (str "Basic " (base64-auth client)))
                      ((fn [s] (request s "/token"
                                        :request-method :post
                                        :params {:grant_type "authorization_code"
                                                 :code (extract-access-code s)
                                                 :redirect_uri redirect-uri}))))]

        ;; then
        (let [{:keys [status body]} (:response state)]

          status => 200
          (slurp body) => (contains "access_token"))))
