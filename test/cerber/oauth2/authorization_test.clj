(ns cerber.oauth2.authorization-test
  (:require [cerber
             [test-utils :as utils]
             [config :refer [app-config]]
             [handlers :as handlers]]
            [cerber.oauth2.context :as ctx]
            [compojure.core :refer [defroutes routes wrap-routes GET POST]]
            [midje.sweet :refer :all]
            [peridot.core :refer :all]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [cheshire.core :as json]))

(def redirect-uri "http://localhost")
(def scope "photo:read")
(def state "123ABC")

(def client (utils/create-test-client scope redirect-uri))

(def user-active   (utils/create-test-user "pass"))
(def user-inactive (utils/create-test-user {:login (utils/random-string 12)
                                            :enabled? false}
                                           "pass"))

(defroutes oauth-routes
  (GET  "/authorize" [] handlers/authorization-handler)
  (POST "/approve"   [] handlers/client-approve-handler)
  (GET  "/refuse"    [] handlers/client-refuse-handler)
  (POST "/token"     [] handlers/token-handler)
  (GET  "/login"     [] handlers/login-form-handler)
  (POST "/login"     [] handlers/login-submit-handler))

(defroutes restricted-routes
  (GET "/users/me" [] (fn [req]
                        {:status 200
                         :body (select-keys (::ctx/user req) [:login :name :email :roles :permissions])})))

(def app (-> restricted-routes
             (wrap-routes handlers/wrap-authorized)
             (wrap-defaults api-defaults)))

(fact "Enabled user with valid password is redirected to langing page when successfully logged in."
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request "/login") ;; get csrf
                      (utils/request-secured "/login"
                                             :request-method :post
                                             :params {:username (:login user-active)
                                                      :password "pass"}))]

        (get-in state [:response :status]) => 302
        (get-in state [:response :headers "Location"]) => (:landing-url app-config)))

(fact "Enabled user with wrong credentials is redirected back to login page with failure info provided."
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request "/login")
                      (utils/request-secured "/login"
                                             :request-method :post
                                             :params {:username (:login user-active)
                                                      :password ""}))]

        (get-in state [:response :status]) => 200
        (get-in state [:response :body]) => (contains "failed")))

(fact "Inactive user is not able to log in."
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request "/login")
                      (utils/request-secured "/login"
                                             :request-method :post
                                             :params {:username (:login user-inactive)
                                                      :password "pass"}))]

        (get-in state [:response :status]) => 200
        (get-in state [:response :body]) => (contains "failed")))

(fact "Client may receive its token in Authorization Code Grant scenario."
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request (str "/authorize?response_type=code"
                                    "&client_id=" (:id client)
                                    "&scope=" scope
                                    "&state=" state
                                    "&redirect_uri=" redirect-uri))

                      ;; login window
                      (follow-redirect)
                      (utils/request-secured "/login"
                                             :request-method :post
                                             :params {:username (:login user-active)
                                                      :password "pass"})

                      ;; authorization prompt
                      (follow-redirect)
                      (utils/request-secured "/approve"
                                             :request-method :post
                                             :params {:client_id (:id client)
                                                      :response_type "code"
                                                      :redirect_uri redirect-uri})

                      ;; having access code received - final request for acess-token
                      (header "Authorization" (str "Basic " (utils/base64-auth client)))
                      ((fn [s] (request s "/token"
                                        :request-method :post
                                        :params {:grant_type "authorization_code"
                                                 :code (utils/extract-access-code s)
                                                 :redirect_uri redirect-uri}))))]

        (let [{:keys [status body]} (:response state)
              {:keys [access_token expires_in refresh_token]} (json/parse-string (slurp body) true)]

          status        => 200
          access_token  => truthy
          refresh_token => truthy
          expires_in    => truthy

          ;; authorized request to /users/me should contain user's login
          (utils/request-authorized (session app) "/users/me" access_token) => (contains (:login user-active)))))

(fact "Client is redirected with error message when tries to get an access-token with undefined scope."
      (let [scope  "profile" ;; scope not defined in cerber-test.edn
            client (utils/create-test-client scope redirect-uri)
            state  (-> (session (wrap-defaults oauth-routes api-defaults))
                       (header "Accept" "text/html")
                       (request (str "/authorize?response_type=code"
                                     "&client_id=" (:id client)
                                     "&scope=" scope
                                     "&state=" state
                                     "&redirect_uri=" redirect-uri)))]

        (let [{:keys [status headers]} (:response state), location (get headers "Location")]
          status => 302
          location => (contains "error=invalid_scope"))))

(fact "Client may provide no scope at all (scope is optional)."
      (let [client (utils/create-test-client "" redirect-uri)
            state  (-> (session (wrap-defaults oauth-routes api-defaults))
                       (header "Accept" "text/html")
                       (request (str "/authorize?response_type=code"
                                     "&client_id=" (:id client)
                                     "&state=" state
                                     "&redirect_uri=" redirect-uri)))]

        (let [{:keys [status headers]} (:response state), location (get headers "Location")]
          status => 302
          location =not=> (contains "error=invalid_scope"))))

(fact "Client may receive its token in Implict Grant scenario."
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "text/html")
                      (request (str "/authorize?response_type=token"
                                    "&client_id=" (:id client)
                                    "&scope=" scope
                                    "&state=" state
                                    "&redirect_uri=" redirect-uri))

                      ;; login window
                      (follow-redirect)
                      (utils/request-secured "/login"
                                             :request-method :post
                                             :params {:username (:login user-active)
                                                      :password "pass"})

                      ;; response with token
                      (follow-redirect))]

        (let [{:keys [status headers]} (:response state), location (get headers "Location")]

          status   => 302
          location => (contains "access_token")
          location => (contains "expires_in")
          location =not=> (contains "refresh_token")

          ;; authorized request to /users/me should contain user's login
          (let [token (second (re-find #"access_token=([^\&]+)" location))
                login (:login user-active)]

            (utils/request-authorized (session app) "/users/me" token) => (contains login)))))

(fact "Client may receive its token in Resource Owner Password Credentials Grant scenario for enabled user."
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "application/json")
                      (header "Authorization" (str "Basic " (utils/base64-auth client)))
                      (request "/token"
                               :request-method :post
                               :params {:username (:login user-active)
                                        :password "pass"
                                        :grant_type "password"}))]

        (let [{:keys [status body]} (:response state)
              {:keys [access_token expires_in refresh_token]} (json/parse-string (slurp body) true)]

          status        => 200
          access_token  => truthy
          refresh_token => truthy
          expires_in    => truthy

          ;; authorized request to /users/me should contain user's login
          (utils/request-authorized (session app) "/users/me" access_token) => (contains (:login user-active)))))

(fact "Client cannot receive token in Resource Owner Password Credentials Grant scenario for disabled user."
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "application/json")
                      (header "Authorization" (str "Basic " (utils/base64-auth client)))
                      (request "/token"
                               :request-method :post
                               :params {:username (:login user-inactive)
                                        :password "pass"
                                        :grant_type "password"}))]

        (get-in state [:response :status]) => 401))

(fact "Client may receive its token in Client Credentials Grant."
      (let [state (-> (session (wrap-defaults oauth-routes api-defaults))
                      (header "Accept" "application/json")
                      (header "Authorization" (str "Basic " (utils/base64-auth client)))
                      (request "/token"
                               :request-method :post
                               :params {:grant_type "client_credentials"}))]

        (let [{:keys [status body]} (:response state)
              {:keys [access_token expires_in refresh_token]} (json/parse-string (slurp body) true)]

          status        => 200
          access_token  => truthy
          refresh_token => falsey
          expires_in    => truthy

          ;; authorized request to /users/me should not reveal user's info
          (utils/request-authorized (session app) "/users/me" access_token) => (contains "\"login\":null"))))
