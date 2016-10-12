(ns cerber.oauth2.authorization-test
  (:require [cerber
             [common :refer :all]
             [config :refer [app-config]]
             [handlers :as handlers]]
            [cerber.stores
             [client :as c]
             [user :as u]
             [session :as s]]
            [compojure.core :refer [defroutes GET POST]]
            [midje.sweet :refer :all]
            [peridot.core :refer :all]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]))

(def redirect-uri "http://localhost")
(def scope "photo:read")
(def state "123ABC")

(defroutes oauth-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/approve"   [] handlers/client-approve-handler)
  (GET  "/refuse"    [] handlers/client-refuse-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))

(def client (c/create-client "http://foo.com" [redirect-uri] [scope]  nil ["moderator"] false))

(fact "Enabled user with valid password is redirected to langing page when successfully logged in."
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

(fact "Enabled user with wrong credentials is redirected back to login page with failure info provided."
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

        ;; then
        (get-in state [:response :status]) => 200
        (get-in state [:response :body]) => (contains "failed")))

(fact "Inactive user is not able to log in."
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

(fact "Client may receive its token in Authorization Code Grant scenario."
      (u/purge-users)
      (s/purge-sessions)

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
                      (request-secured "/approve"
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
        (let [{:keys [status body]} (:response state), token (slurp body)]
          status => 200
          token => (contains "access_token")
          token => (contains "expires_in")
          token => (contains "refresh_token"))))

(fact "Client may receive its token in Implict Grant scenario."
      (u/purge-users)
      (s/purge-sessions)

      ;; given
      (u/create-user {:login "nioh" :enabled true} "alamakota")

      ;; when
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request (str "/authorize?response_type=token"
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

                      ;; response with token
                      (follow-redirect))]

        ;; then
        (let [{:keys [status headers]} (:response state), location (get headers "Location")]
          status => 302
          location => (contains "access_token")
          location => (contains "expires_in")
          location =not=> (contains "refresh_token"))))

(fact "Client may receive its token in Resource Owner Password Credentials Grant scenario for enabled user."
      (u/purge-users)

      ;; given
      (u/create-user {:login "nioh" :enabled true} "alamakota")

      ;; when
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "application/json")
                      (header "Authorization" (str "Basic " (base64-auth client)))
                      (request "/token"
                               :request-method :post
                               :params {:username "nioh"
                                        :password "alamakota"
                                        :grant_type "password"}))]

        ;; then
        (let [{:keys [status body]} (:response state), token (slurp body)]
          status => 200
          token => (contains "access_token")
          token => (contains "expires_in")
          token => (contains "refresh_token"))))

(fact "Client cannot receive token in Resource Owner Password Credentials Grant scenario for disabled user."
      (u/purge-users)

      ;; given
      (u/create-user {:login "nioh" :enabled false} "alamakota")

      ;; when
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "application/json")
                      (header "Authorization" (str "Basic " (base64-auth client)))
                      (request "/token"
                               :request-method :post
                               :params {:username "nioh"
                                        :password "alamakota"
                                        :grant_type "password"}))]

        ;; then
        (get-in state [:response :status]) => 401))

(fact "Client may receive its token in Client Credentials Grant."
      (u/purge-users)

      ;; when
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "application/json")
                      (header "Authorization" (str "Basic " (base64-auth client)))
                      (request "/token"
                               :request-method :post
                               :params {:grant_type "client_credentials"}))]

        ;; then
        (let [{:keys [status body]} (:response state), token (slurp body)]
          status => 200
          token => (contains "access_token")
          token => (contains "expires_in")
          token =not=> (contains "refresh_token"))))
