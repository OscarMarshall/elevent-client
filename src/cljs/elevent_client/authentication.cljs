(ns elevent-client.authentication
  (:require [goog.crypt.base64 :as b64]

            [cljs.core.async :refer [chan put!]]

            [ajax.core :refer [DELETE GET POST PUT]]
            [datascript :as d]

            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.config :as config]))

;; User Account
;; =============================================================================

(defn sign-in! [form]
  (let [{:keys [Email Password]} form
        auth-string (b64/encodeString (str Email ":" Password))]
    (GET (str config/https-url "/token")
         {:format          :json
          :response-format :json
          :keywords?       true
          :headers         {:Authorization (str "Basic " auth-string)}
          :handler         (fn [response]
                             (swap! state/session assoc
                                    :token (:Token response))
                             (swap! state/session assoc-in
                                    [:user :Email] Email)
                             (reset! state/messages {})
                             (put! state/api-refresh-chan true)
                             (put! state/add-messages-chan
                                   [:sign-in-succeeded
                                    [:positive "Sign in succeeded"]])
                             (js/location.replace (routes/events)))
          :error-handler   #(put! state/add-messages-chan
                                  [:sign-in-failed
                                   [:negative "Sign in failed"]])})))

(defn sign-out! []
  (swap! state/session dissoc :token :user :stripe-token :payment-info)
  (put! state/api-refresh-chan true)
  (set! js/window.location (routes/home)))

(defn sign-up! [form]
  (put! state/auth-sign-up-chan form))
