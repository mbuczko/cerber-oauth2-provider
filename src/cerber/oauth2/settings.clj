(ns cerber.oauth2.settings)

(def oauth2-settings (atom {:realm "http://localhost"
                            :authentication-url "/login"
                            :landing-url "/"
                            :token-valid-for 300
                            :authcode-valid-for 180
                            :session-valid-for 3600}))

(defn update-settings
  [settings]
  (swap! oauth2-settings merge settings))

(doseq [s (keys @oauth2-settings)]
  (intern *ns*
          (symbol (name s))
          (fn
            ([]    (s @oauth2-settings))
            ([val] (swap! oauth2-settings update s val)))))
