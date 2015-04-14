(ns elevent-client.pages.payments
  (:require [elevent-client.state :as state]
            [elevent-client.components.payments :as payments]))

(defn payments-page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    ; TODO: expire stripe token if payment info changes
    [(with-meta identity
                {:component-did-mount
                 (fn [] (.change (js/$ "#payments-form")
                                 #_(swap! state/session assoc :stripe-token nil)
                                 #(prn "changed")))})
     [:form#payments-form.ui.form
    [payments/component]]]]])
