(ns cerber.oauth2.error-test
  (:require [midje.sweet :refer :all]
            [cerber.test-utils :refer [with-stores]]
            [cerber.oauth2.authorization :refer [authorize!]]
            [cerber.oauth2.core :as core]))

(fact "Authorization fails with meaningful error message when requested by unknown client, scope or mismatched redirect_uri."
      (with-stores :in-memory

        ;; given
        (let [user   (core/create-user {:login "foo"} "secret")
              client (core/create-client "test-client"
                                         ["http://localhost"]
                                         ["authorization_code"]
                                         ["photo"]
                                         true
                                         true)
              req {:request-method :get
                   :params  {:response_type "code"
                             :redirect_uri "http://localhost"
                             :client_id (:id client)
                             :scope "photo"
                             :state "123ABC"}
                   :session {:login "foo"}}]

          ;; then
          (:status (authorize! req)) => 302
          (:error (authorize! (assoc-in req [:params :client_id] "foo"))) => "invalid_request"
          (:error (authorize! (assoc-in req [:params :scope] "dummy"))) => "invalid_scope"
          (:error (authorize! (assoc-in req [:params :redirect_uri] "http://bar.bazz"))) => "invalid_redirect_uri")))
