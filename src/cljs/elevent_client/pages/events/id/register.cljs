(ns elevent-client.pages.events.id.register
  (:require [goog.string :as string]

            [validateur.validation :refer [format-of
                                           presence-of
                                           validation-set]]
            [reagent.core :as r :refer [atom]]
            [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.core :refer [after?]]
            [cljs-time.format :refer [unparse]]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.payments :as payments]
            [elevent-client.components.input :as input]
            [elevent-client.locale :as locale]
            [elevent-client.stripe :as stripe]))

(defn page [event-id]
  (let [form (atom {:Email (get-in @state/session [:user :Email])
                    :FirstName (get-in @state/session [:user :FirstName])
                    :LastName (get-in @state/session [:user :LastName])})
        validator (validation-set (presence-of :Email)
                                  (presence-of :FirstName)
                                  (presence-of :LastName)
                                  (format-of :Email :format #"@"))]
    (fn []
      (let [{:keys [Email FirstName LastName]}
            @form

            errors
            (validator @form)

            event
            (into {} (d/entity @api/events-db event-id))

            register
            (fn [form]
              (fn [callback]
                (when (empty? errors)
                  ; TODO: maybe this logic is wrong? Might want to buy ticket
                  ;       even if not required
                  (if (:RequiresPayment event)
                    (stripe/renew-token!
                      (fn [] (api/attendees-endpoint
                               :create
                               {:UserId (get-in @state/session [:user :UserId])
                                :EventId event-id
                                :Token (:stripe-token @state/session)
                                :Amount (:TicketPrice event)}
                               #(do
                                  (callback)
                                  (swap! state/session dissoc :stripe-token)
                                  (js/location.replace (routes/events))))))
                    (api/attendees-endpoint
                      :create
                      {:UserId (get-in @state/session [:user :UserId])
                       :EventId event-id}
                      #(do
                         (callback)
                         (js/location.replace (routes/events))))))))]
        (when (seq event)
          [:div.ui.stackable.page.grid
           [:div.sixteen.wide.column
            [:div.ui.segment
             [:div.ui.vertical.segment
              [:h2.ui.dividing.header
               (str "Register for " (:Name event))]
              [:div.meta
               [:strong "Date: "]
               (let [start (from-string (:StartDate event))
                     end   (from-string (:EndDate   event))]
                 (str (unparse locale/datetime-formatter start)
                      (when (after? end start)
                        (str " to " (unparse locale/datetime-formatter end)))))]
              [:div.meta
               [:strong "Venue: "]
               (:Venue event)]
              (when (> (:TicketPrice event) 0)
                [:div.meta
                 [:strong "Ticket Price: "]
                 (string/format "$%.2f" (:TicketPrice event))])
              [:div.description
               (:Description event)]]
             [:div.ui.vertical.segment
              [:form.ui.form
               [:div.one.field
                [:div.required.field
                 [:label "Email"]
                 [input/component :text {:disabled true}
                  (r/wrap Email swap! form assoc :Email)]]]
               [:div.two.fields
                [:div.required.field
                 [:label "First Name"]
                 [input/component :text {:disabled true}
                  (r/wrap FirstName swap! form assoc :FirstName)]]
                [:div.required.field
                 [:label "Last Name"]
                 [input/component :text {:disabled true}
                  (r/wrap LastName swap! form assoc :LastName)]]]
               ; TODO: use TicketPrice > 0 instead of RequiresPayment
               (when (:RequiresPayment event)
                 [payments/component])]
              [:div.ui.divider]
              [action-button/component
               {:class [:primary
                        (when (or (seq errors)
                                  (and (:RequiresPayment event)
                                       (nil? (:payment-info @state/session))))
                          :disabled)]}
               "Register"
               (register @form)]]]]])))))

(routes/register-page routes/event-register-chan #'page)
