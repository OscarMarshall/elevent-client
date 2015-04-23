(ns elevent-client.pages.events.id.schedule
  (:require [clojure.set :as set]

            [datascript :as d]
            [reagent.core :as r :refer [atom]]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.stripe :as stripe]
            [elevent-client.components.payments :as payments]
            [elevent-client.components.activity-details :as activity-details]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.schedule :as schedule]
            [elevent-client.components.logo :as logo]))

(defn page [event-id]
  (let [cart-activities (atom #{})

        add-activity!
        (fn [user-id activity-id]
          (api/schedules-endpoint :create
                              {:ActivityId activity-id
                               :UserId     user-id}
                              nil))

        event-logo (atom nil)]
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

         mandatory-activities
         (into #{}
               (d/q '[:find [?activity-id ...]
                      :in $attendees $mandates ?event-id ?user-id
                      :where
                      [$attendees ?attendee-id :UserId     ?user-id]
                      [$attendees ?attendee-id :GroupId    ?group-id]
                      [$mandates  ?mandate-id  :GroupId    ?group-id]
                      [$mandates  ?mandate-id  :ActivityId ?activity-id]]
                    @api/attendees-db
                    @api/mandates-db
                    (get-in @state/session [:user :UserId])))

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
           (swap! cart-activities conj [activity-id]))

         remove-activity-from-cart!
         (fn [activity-id]
           (swap! cart-activities disj [activity-id]))

         add-cart-activities!
         (fn [user-id cart-activities]
           (fn [callback]
             (let [activities (into [] (map (fn [[activity-id]]
                                              activity-id)
                                            @cart-activities))]
               (stripe/renew-token!
                 (fn [] (api/schedules-endpoint :bulk
                                                {:UserId      user-id
                                                 :Token       (:stripe-token @state/session)
                                                 :ActivityIds activities}
                                                #(do
                                                   (reset! cart-activities #{})
                                                   (callback))
                                                callback))
                 callback))))]
        (when (seq event)
          (when (and (:HasLogo event)
                     (not @event-logo))
            (api/api-call :read
                          (str "/events/" event-id "/logos")
                          {}
                          (fn [json] (reset! event-logo (:URL json)))
                          (fn [] (reset! event-logo nil))))
          [:div.sixteen.wide.column
           [:div.ui.sixteen.column.grid
            [:div.column {:class (str (if (:HasLogo event)
                                        "thirteen"
                                        "sixteen")
                                      " wide")}
             [:div.ui.segment
              [:div.ui.vertical.segment
               [:h2.ui.header
                (str (get-in @state/session [:user :FirstName]) "'s Schedule for "
                     (:Name event))]]
              [:div.ui.vertical.segment
               [schedule/component scheduled-activities
                (list [:i.red.remove.icon] "Remove")
                (fn [schedule-id activity-id]
                  (api/schedules-endpoint :delete
                                          (d/entity @api/schedules-db
                                                    schedule-id)
                                          nil))
                nil
                (fn [activity-id]
                  (contains? mandatory-activities activity-id))]]
              [:div.ui.vertical.segment
               (if (seq event-activities)
                 [:div.ui.divided.items
                  (doall
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
                              [:i.right.chevron.icon]])]]])))]
                 [:p "There are no activities for this event"])]]
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
                  [action-button/component
                   {:class (str "primary" (when (nil? (:payment-info @state/session))
                                            " disabled"))}
                   "Confirm Payment and Add Activities"
                   (add-cart-activities! (get-in @state/session
                                                 [:user :UserId])
                                         cart-activities)]]]])]
            [logo/component @event-logo]]])))))

(routes/register-page routes/event-schedule-chan #'page true)
