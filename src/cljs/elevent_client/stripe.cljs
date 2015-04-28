;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.stripe
  (:require [cljs.core.async :refer [<! >! chan put! take!]]

            [elevent-client.state :as state])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn renew-token!
  "Uses the :payment-info found in the state/session atom to renew the Stripe
  card token. Stores the :id of the response as the :stripe-token value in the
  state/session atom."
  [callback error-callback]
  (let [response-chan (chan)]
    (Stripe.card.createToken (clj->js (:payment-info @state/session))
                             #(put! response-chan (js->clj %& :keywordize-keys true)))
    (go
      (let [response (second (<! response-chan))]
       (if (not (:error response))
        (do
          (swap! state/session assoc :stripe-token (:id response))
          (callback))
        (error-callback))))))
