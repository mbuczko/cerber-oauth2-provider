(ns cerber.oauth2.settings)

(def globals (atom {:realm "http://localhost"
                    :authentication-url "/login"
                    :landing-url "/"
                    :token-valid-for 300
                    :authcode-valid-for 180
                    :session-valid-for 3600}))

(defn update-settings
  [settings]
  (swap! globals merge settings))

(doseq [s (keys @globals)]
  (intern *ns*
          (symbol (name s))
          (fn
            ([]    (s @globals))
            ([val] (swap! globals update s val)))))
