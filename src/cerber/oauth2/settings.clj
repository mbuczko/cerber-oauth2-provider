(ns cerber.oauth2.settings)

(def defaults (atom {:realm "http://localhost"
                     :authentication-url "/login"
                     :unauthorized-url "/login"
                     :landing-url "/"
                     :token-valid-for 300
                     :authcode-valid-for 180
                     :session-valid-for 3600}))

(defn update-settings
  [settings]
  (swap! defaults merge settings))

(doseq [s (keys @defaults)]
  (intern *ns*
          (symbol (name s))
          (fn
            ([]    (s @defaults))
            ([val] (swap! defaults update s (constantly val))))))
