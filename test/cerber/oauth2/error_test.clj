(ns cerber.oauth2.error-test
  (:require [midje.sweet :refer :all]
            [cerber.common :refer :all]
            [cerber.oauth2.authorization :refer [authorize!]]
            [cerber.stores.client :refer [create-client create-client-store with-client-store]]))

(fact "Authorization fails when requested by unknown client."
      (with-client-store (create-client-store :in-memory)

        ;; given
        (let [client (create-client "http://localhost" ["http://localhost"] ["photo"] nil nil false)
              req {:request-method :get
                   :params {:response_type "code"}}]

          ;; then
          (:error (authorize! (assoc-in req [:params :client_id] (:id client)))) => "invalid_request"
          (:error (authorize! (assoc-in req [:params :client_id] "foo"))) => "invalid_request")))

(fact "Authorization fails when requested with unknown scope."
      (with-client-store (create-client-store :in-memory)

        ;; given
        (let [client (create-client "http://localhost" ["http://localhost"] ["photo"] nil nil false)
              req {:request-method :get
                   :params {:response_type "code"
                            :client_id (:id client)}}]

          ;; then
          (:error (authorize! (assoc-in req [:params :scope] "foo"))) => "invalid_scope"
          (:error (authorize! (assoc-in req [:params :scope] "photo"))) => "invalid_request")))
