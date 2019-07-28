(ns cerber.oauth2.error-test
  (:require [cerber.test-utils :refer [with-stores]]
            [cerber.oauth2.authorization :refer [authorize!]]
            [cerber.oauth2.core :as core]
            [midje.sweet :refer [fact tabular => =not=> contains just truthy falsey]]))

(fact "Authorization fails with meaningful error message when requested by unknown client, scope or mismatched redirect_uri."
      (with-stores :in-memory

        ;; given
        (let [user (core/create-user "foo" "secret")
              client (core/create-client ["authorization_code"]
                                         ["http://localhost"]
                                         :info "test-client"
                                         :scopes ["photo"]
                                         :enabled? true
                                         :approved? true)
              req {:request-method :get
                   :uri "/users/me"
                   :scheme "http"
                   :params  {:response_type "code"
                             :redirect_uri "http://localhost"
                             :client_id (:id client)
                             :scope "photo"
                             :state "123ABC"}
                   :session {:login "foo"}}]

          ;; then
          (:status (authorize! req)) => 302
          (:error (authorize! (assoc-in req [:params :client_id] "foo"))) => "bad_request"
          (:error (authorize! (assoc-in req [:params :scope] "dummy"))) => "invalid_scope"
          (:error (authorize! (assoc-in req [:params :redirect_uri] "http://bar.bazz"))) => "invalid_redirect_uri")))
