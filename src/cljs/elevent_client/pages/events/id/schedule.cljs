(ns elevent-client.pages.events.id.schedule
  (:require [clojure.set :as set]

            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.stripe :as stripe]
            [elevent-client.components.payments :as payments]
            [elevent-client.components.activity-details :as activity-details]
            [elevent-client.components.schedule :as schedule]))

(defn page [event-id]
  (let [cart-activities (atom #{})

        add-activity!
        (fn [user-id activity-id]
          (api/schedules-endpoint :create
                              {:ActivityId activity-id
                               :UserId     user-id}
                              nil))

        remove-activity!
        (fn [schedule _]
          (api/schedules-endpoint :delete schedule nil))]
    (fn [event-id]
      (let
        [event (into {} (d/entity @api/events-db event-id))

         scheduled-activities
         (d/q '[:find ?schedule-id ?activity-id
                :in $activities $schedules ?event-id ?user-id
                :where
                [$activities ?activity-id :EventId ?event-id]
                [$schedules  ?schedule-id :UserId     ?user-id]
                [$schedules  ?schedule-id :ActivityId ?activity-id]]
              @api/activities-db
              @api/schedules-db
              event-id
              (get-in @state/session [:user :UserId]))

         event-activities
         (d/q '[:find ?activity-id
                :in $ ?event-id
                :where
                [?activity-id :EventId ?event-id]]
              @api/activities-db
              event-id)

         unscheduled-activities
         (set/difference event-activities
                         (into #{} (map #(vector (second %))
                                        scheduled-activities))
                         @cart-activities)

         add-activity-to-cart!
         (fn [activity-id]
           (prn activity-id)
           (swap! cart-activities conj [activity-id]))

         remove-activity-from-cart!
         (fn [activity-id]
           (prn activity-id)
           (swap! cart-activities disj [activity-id]))

         add-cart-activities!
         (fn [e user-id cart-activities]
           (.preventDefault e)
           (let [activities (into [] (map (fn [[activity-id]]
                                            activity-id)
                                          @cart-activities))]
             (stripe/renew-token!
               (fn [] (api/schedules-endpoint :bulk
                                          {:UserId      user-id
                                           :Token       (:stripe-token @state/session)
                                           :ActivityIds activities}
                                          #(reset! cart-activities #{}))))))]
        (when (seq event)
          [:div.sixteen.wide.column
           [:div.ui.segment
            [:div.ui.vertical.segment
             [:h2.ui.header
              (str (get-in @state/session [:user :FirstName]) "'s Schedule for "
                   (:Name event))]]
            [:div.ui.vertical.segment
             [schedule/component scheduled-activities
              (list [:i.red.remove.icon] "Remove")
              remove-activity!]]
            [:div.ui.vertical.segment
             [:div.ui.divided.items
              (for [[activity-id] unscheduled-activities]
                ^{:key activity-id}
                (let [activity
                      (when activity-id
                        (d/entity @api/activities-db activity-id))]
                  [:div.item
                   [:div.content
                    [:div.header (:Name activity)]
                    [activity-details/component activity]
                    [:div.extra
                     (if (> (:TicketPrice activity) 0)
                       [:div.ui.right.floated.button
                        {:on-click #(add-activity-to-cart!
                                      (:ActivityId activity))}
                        "Add to cart"
                        [:i.right.chevron.icon]]
                       [:div.ui.right.floated.primary.button
                        {:on-click #(add-activity! (get-in @state/session
                                                           [:user :UserId])
                                                   (:ActivityId activity))}
                        "Add"
                        [:i.right.chevron.icon]])]]]))]]]
           (when (seq @cart-activities)
             [:div.ui.segment
              [:div.ui.vertical.segment
               [:div.ui.vertical.segment
                [:h2.ui.dividing.header "Cart"]
                [:div.ui.divided.items
                 (for [[activity-id] @cart-activities]
                   ^{:key activity-id}
                   (let [activity
                         (when activity-id
                           (d/entity @api/activities-db activity-id))]
                     [:div.item
                      [:div.content
                       [:div.header (:Name activity)]
                       [activity-details/component activity]
                       [:div.extra
                        [:div.ui.right.floated.button
                         {:on-click #(remove-activity-from-cart!
                                       (:ActivityId activity))}
                         [:i.red.remove.icon]
                         "Remove"]]]]))]]
               [:div.ui.vertical.segment
                [:form.ui.form
                 [payments/component]]
                [:div.ui.divider]
                [:button.ui.primary.button
                 {:type :submit
                  :class (when (nil? (:payment-info @state/session))
                           :disabled)
                  :on-click #(add-cart-activities! % (get-in @state/session
                                                             [:user :UserId])
                                                   cart-activities)}
                 "Confirm Payment and Add Activities"]]]])])))))

(routes/register-page routes/event-schedule-chan #'page)