(ns elevent-client.stripe
  (:require [cljs.core.async :refer [<! >! chan put! take!]]

            [elevent-client.state :as state])
  (:require-macros [cljs.core.async.macros :refer [go go-loop]]))

(defn renew-token! [callback]
  (let [response-chan (chan)]
    (Stripe.card.createToken (clj->js (:payment-info @state/session))
                             #(put! response-chan (js->clj %& :keywordize-keys true)))
    (go
      (let [response (second (<! response-chan))]
       (when (not (:error response))
        (swap! state/session assoc :stripe-token (:id response))
        (callback))))))
