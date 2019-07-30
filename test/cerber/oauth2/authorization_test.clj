(ns cerber.oauth2.authorization-test
  (:require [cerber
             [db :as db]
             [test-utils :as utils]
             [handlers :as handlers]]
            [cerber.oauth2.context :as ctx]
            [cheshire.core :as json]
            [compojure.core :refer [defroutes routes wrap-routes GET POST]]
            [peridot.core :refer [request header session follow-redirect]]
            [ring.middleware.defaults :refer [api-defaults wrap-defaults]]
            [midje.sweet :refer [fact tabular => =not=> contains just truthy falsey]]))

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

(defroutes restricted-routes
  (GET "/users/me" [] (fn [req]
                        {:status 200
                         :body (::ctx/user req)})))

(def app (-> restricted-routes
             (wrap-routes handlers/wrap-authorized)
             (wrap-defaults api-defaults)))

(fact "Enabled user with valid password is redirected to landing page when successfully logged in."
      (utils/with-storage :sql
        (let [user  (utils/create-test-user :password "pass")
              state (-> (session (wrap-defaults oauth-routes api-defaults))
                        (header "Accept" "text/html")
                        (request "/login") ;; get anit-csrf token
                        (utils/request-secured "/login"
                                               :request-method :post
                                               :params {:username (:login user)
                                                        :password "pass"}))]

          (get-in state [:response :status]) => 302
          (get-in state [:response :headers "Location"]) => "http://localhost/")))

(fact "Enabled user with valid password gets HTTP 200 OK with landing-url in a body when logging in with XHR request."
      (utils/with-storage :sql
        (let [user  (utils/create-test-user :password "pass")
              state (-> (session (wrap-defaults oauth-routes api-defaults))
                        (header "Accept" "application/json")
                        (header "X-Requested-With" "XMLHttpRequest")
                        (request "/login") ;; get anti-csrf token
                        (utils/request-secured "/login"
                                               :request-method :post
                                               :params {:username (:login user)
                                                        :password "pass"}))]

          (get-in state [:response :status]) => 200
          (get-in state [:response :body]) => {:landing-url "/"})))

(fact "Enabled user with wrong credentials is redirected back to login page with failure info provided."
      (utils/with-storage :sql
        (let [user  (utils/create-test-user :password "pass")
              state (-> (session (wrap-defaults oauth-routes api-defaults))
                        (header "Accept" "text/html")
                        (request "/login")  ;; get anti-csrf token
                        (utils/request-secured "/login"
                                               :request-method :post
                                               :params {:username (:login user)
                                                        :password ""}))]

          (get-in state [:response :status]) => 200
          (get-in state [:response :body]) => (contains "failed"))))

(fact "Enabled user with wrong credentials gets HTTP 401 Unauthorized when logging in with XHR request."
      (utils/with-storage :sql
        (let [user  (utils/create-test-user :password "pass")
              state (-> (session (wrap-defaults oauth-routes api-defaults))
                        (header "Accept" "application/json")
                        (header "X-Requested-With" "XMLHttpRequest")
                        (request "/login")  ;; get anti-csrf token
                        (utils/request-secured "/login"
                                               :request-method :post
                                               :params {:username (:login user)
                                                        :password ""}))]

          (get-in state [:response :status]) => 401)))

(fact "Inactive user is not able to log in."
      (utils/with-storage :sql
        (let [user  (utils/create-test-user :enabled? false
                                            :password "pass")
              state (-> (session (wrap-defaults oauth-routes api-defaults))
                        (header "Accept" "text/html")
                        (request "/login")  ;; get anti-csrf token
                        (utils/request-secured "/login"
                                               :request-method :post
                                               :params {:username (:login user)
                                                        :password "pass"}))]

          (get-in state [:response :status]) => 200
          (get-in state [:response :body]) => (contains "failed"))))

(fact "Inactive user is not able to log in with XHR request."
      (utils/with-storage :sql
        (let [user  (utils/create-test-user :enabled? false
                                            :password "pass")
              state (-> (session (wrap-defaults oauth-routes api-defaults))
                        (header "Accept" "application/json")
                        (header "X-Requested-With" "XMLHttpRequest")
                        (request "/login")  ;; get anti-csrf token
                        (utils/request-secured "/login"
                                               :request-method :post
                                               :params {:username (:login user)
                                                        :password "pass"}))]

          (get-in state [:response :status]) => 401)))

(fact "Unapproved client may receive its token in Authorization Code Grant scenario. Needs user's approval."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope)
              user   (utils/create-test-user :password "pass")
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
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
                                                :params {:username (:login user)
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
            (-> (session app)
                (utils/request-authorized "/users/me" access_token)
                :login) => (:login user)))))

(fact "Approved client may receive its token in Authorization Code Grant scenario. Doesn't need user's approval."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope :approved? true)
              user   (utils/create-test-user :password "pass")
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
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
                                                :params {:username (:login user)
                                                         :password "pass"})
                         ;; follow authorization link
                         (follow-redirect)

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
            (-> (session app)
                (utils/request-authorized "/users/me" access_token)
                :login) => (:login user)))))

(fact "Client is redirected with error message when tries to get an access-token with undefined scope."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope)
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
                         (header "Accept" "text/html")
                         (request (str "/authorize?response_type=code"
                                       "&client_id=" (:id client)
                                       "&scope=profile"
                                       "&state=" state
                                       "&redirect_uri=" redirect-uri)))]

          (let [{:keys [status headers]} (:response state), location (get headers "Location")]
            status => 302
            location => (contains "error=invalid_scope")))))

(fact "Client may provide no scope at all (scope is optional)."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope "")
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
                         (header "Accept" "text/html")
                         (request (str "/authorize?response_type=code"
                                       "&client_id=" (:id client)
                                       "&state=" state
                                       "&redirect_uri=" redirect-uri)))]

          (let [{:keys [status headers]} (:response state), location (get headers "Location")]
            status => 302
            location =not=> (contains "error=invalid_scope")))))

(fact "Client may receive its token in Implict Grant scenario."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope)
              user   (utils/create-test-user :password "pass")
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
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
                                                :params {:username (:login user)
                                                         :password "pass"})
                         ;; response with token
                         (follow-redirect))]

          (let [{:keys [status headers]} (:response state), location (get headers "Location")]

            status   => 302
            location => (contains "access_token")
            location => (contains "expires_in")
            location =not=> (contains "refresh_token")

            ;; authorized request to /users/me should contain user's login
            (let [token (second (re-find #"access_token=([^\&]+)" location))]
              (-> (session app)
                  (utils/request-authorized "/users/me" token)
                  :login) => (:login user))))))

(fact "Client may receive its token in Resource Owner Password Credentials Grant scenario for enabled user."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope)
              user   (utils/create-test-user :password "pass")
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
                         (header "Accept" "application/json")
                         (header "Authorization" (str "Basic " (utils/base64-auth client)))
                         (request "/token"
                                  :request-method :post
                                  :params {:username (:login user)
                                           :password "pass"
                                           :grant_type "password"}))]

          (let [{:keys [status body]} (:response state)
                {:keys [access_token expires_in refresh_token]} (json/parse-string (slurp body) true)]

            status        => 200
            access_token  => truthy
            refresh_token => truthy
            expires_in    => truthy

            ;; authorized request to /users/me should contain user's login
            (-> (session app)
                (utils/request-authorized "/users/me" access_token)
                :login) => (:login user)))))

(fact "Client cannot receive token in Resource Owner Password Credentials Grant scenario for disabled user."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope)
              user   (utils/create-test-user :enabled? false
                                             :password "pass")
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
                         (header "Accept" "application/json")
                         (header "Authorization" (str "Basic " (utils/base64-auth client)))
                         (request "/token"
                                  :request-method :post
                                  :params {:username (:login user)
                                           :password "pass"
                                           :grant_type "password"}))]

          (get-in state [:response :status]) => 401)))

(fact "Client may receive its token in Client Credentials Grant."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope)
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
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
            (-> (session app)
                (utils/request-authorized "/users/me" access_token)) => (contains {:login nil})))))

(fact "Active token should be rejected for disabled user."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope)
              user   (utils/create-test-user :password "pass")
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
                         (header "Accept" "application/json")
                         (header "Authorization" (str "Basic " (utils/base64-auth client)))
                         (request "/token"
                                  :request-method :post
                                  :params {:username (:login user)
                                           :password "pass"
                                           :grant_type "password"}))]

          (let [{:keys [status body]} (:response state)
                {:keys [access_token expires_in refresh_token]} (json/parse-string (slurp body) true)]

            status        => 200
            access_token  => truthy
            refresh_token => truthy
            expires_in    => truthy

            (utils/disable-test-user (:login user))

            (-> (session app)
                (header "Authorization" (str "Bearer " access_token))
                (request "/users/me")
                :response
                :status) => 400))))

(fact "Active token should be rejected for disabled client."
      (utils/with-storage :sql
        (let [client (utils/create-test-client redirect-uri :scope scope)
              user   (utils/create-test-user :password "pass")
              state  (-> (session (wrap-defaults oauth-routes api-defaults))
                         (header "Accept" "application/json")
                         (header "Authorization" (str "Basic " (utils/base64-auth client)))
                         (request "/token"
                                  :request-method :post
                                  :params {:username (:login user)
                                           :password "pass"
                                           :grant_type "password"}))]

          (let [{:keys [status body]} (:response state)
                {:keys [access_token expires_in refresh_token]} (json/parse-string (slurp body) true)]

            status        => 200
            access_token  => truthy
            refresh_token => truthy
            expires_in    => truthy

            (utils/disable-test-client (:id client))

            (-> (session app)
                (header "Authorization" (str "Bearer " access_token))
                (request "/users/me")
                :response
                :status) => 400))))
