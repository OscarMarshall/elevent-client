(ns elevent-client.components.payments
  (:require
    [reagent.core :as r]
    [reagent.core :as r :refer [atom]]
    [validateur.validation :refer [format-of presence-of validation-set]]

    [elevent-client.state :as state]
    [elevent-client.components.input :as input]))

(defn component []
  (let [form (atom {})
        validator
        (validation-set
          (presence-of :number)
          (presence-of :cvc)
          (presence-of :exp-date)
          (format-of   :number   :format #"^[0-9]{16}$")
          (format-of   :cvc      :format #"^[0-9]{3}$")
          (format-of   :exp-date :format #"^[0-1][0-9]/20[1-9][0-9]$"))
        editing? (atom false)
        stripe-error (atom nil)
        button-loading? (atom false)]
    (fn []
      (Stripe.setPublishableKey "pk_test_7ntI7D72loXtuO2F15gV0nR0")
      (let [{:keys [number cvc exp-date]} @form
            errors (validator @form)

            response-handler
            (fn [status response]
              (reset! button-loading? false)
              (if response.error
                (do
                  (reset! stripe-error response.error.message))
                (let [[month year]
                      (clojure.string/split (:exp-date @form) #"/" 2)]
                  (reset! stripe-error nil)
                  (reset! editing? false)
                  (swap! state/session assoc :stripe-token response.id)
                  (swap! state/session assoc
                         :payment-info (dissoc (assoc @form
                                                 :exp-month (int month)
                                                 :exp-year (int year))
                                               :exp-date)))))

            create-token
            (fn [e form]
              (when (empty? errors)
                (let [[month year]
                      (clojure.string/split (:exp-date form) #"/" 2)]
                  (reset! button-loading? true)
                  (.preventDefault e)
                  (.stopPropagation e)
                  (Stripe.card.createToken
                    (clj->js (dissoc (assoc form
                                       :exp-month (int month)
                                       :exp-year (int year))
                                     :exp-date))
                    response-handler))))]
        #_(when (:payment-info @state/session)
          (swap! form assoc
                 :number   (str "************"
                                (subs (get-in @state/session
                                              [:payment-info :number])
                                      12))
                 :cvc      (get-in @state/session [:payment-info :cvc])
                 :exp-date (str (get-in @state/session
                                        [:payment-info :exp-month])
                                "/"
                                (get-in @state/session
                                        [:payment-info :exp-year]))))
        (if (or (nil? (:payment-info @state/session))
                @editing?)
          [:div.ui.vertical.segment
           [:h2.ui.dividing.header
            "Payment Info"]
           [:div.two.fields
            [:div.required.field {:class (when (and number (:number errors))
                                           :error)
                                  :on-change #(swap! state/session dissoc
                                                     :payment-info)}
             [:label "Card Number"]
             [input/component :text {}
              (r/wrap number swap! form assoc :number)]]
            [:div.required.field {:class (when (and cvc (:cvc errors))
                                           :error)
                                  :on-change #(swap! state/session dissoc
                                                     :payment-info)}
             [:label "CVC"]
             [:div.two.fields
              [:div.field
               [input/component :password {}
                (r/wrap cvc swap! form assoc :cvc)]]
              [:div.field]]]]
           [:div.two.fields
            [:div.required.field {:class (when (and exp-date (:exp-date errors))
                                           :error)
                                  :on-change #(swap! state/session dissoc
                                                     :payment-info)}
             [:label "Expiration Date"]
             [input/component :text {:placeholder "MM/YYYY"}
              (r/wrap exp-date swap! form assoc :exp-date)]]
            [:div.field]]
           [:button.ui.primary.button
            {:type :submit
             :class (when (seq errors) :disabled)
             :on-click #(create-token % @form)}
            (if @button-loading?
              [:i.spinner.loading.icon]
              "Confirm")]
           [:span.ui.red.compact.message
            {:class (when (nil? @stripe-error) :hidden)}
            @stripe-error]]
          [:div.inline.fields
           [:div.field
            (str "Charging card ending in "
                 (subs (get-in @state/session [:payment-info :number]) 12)
                 ".")]
           [:a.field {:on-click #(swap! state/session dissoc :payment-info)
                      :style {:cursor "pointer"}}
            "Charge different card"
            [:i.right.chevron.icon]]])))))
