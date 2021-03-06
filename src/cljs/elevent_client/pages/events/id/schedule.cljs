;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.events.id.schedule
  (:require [clojure.set :as set]

            [datascript :as d]
            [reagent.core :refer [atom]]

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
  "Add/remove activities to your schedule for an event"
  ; Verify that this user is attending this event
  (if (seq (d/q '[:find ?a
                  :in $ ?event-id ?user-id
                  :where
                  [?a :UserId ?user-id]
                  [?a :EventId ?event-id]]
                @api/attendees-db
                event-id
                (get-in @state/session [:user :UserId])))
    (let [cart-activities (atom #{}) ; store paid activities in cart

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

           ; Activities in the user's schedule
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

           ; Required activities for the user
           mandatory-activities
           (into #{}
                 (d/q '[:find [?activity-id ...]
                        :in $attendees $mandates ?user-id
                        :where
                        [$attendees ?attendee-id :UserId     ?user-id]
                        [$attendees ?attendee-id :GroupId    ?group-id]
                        [$mandates  ?mandate-id  :GroupId    ?group-id]
                        [$mandates  ?mandate-id  :ActivityId ?activity-id]]
                      @api/attendees-db
                      @api/mandates-db
                      (get-in @state/session [:user :UserId])))

           ; All event activities
           event-activities
           (d/q '[:find ?activity-id
                  :in $ ?event-id
                  :where
                  [?activity-id :EventId ?event-id]]
                @api/activities-db
                event-id)

           ; Activities not in the user's schedule
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

           ; Add cart activities to schedule
           add-cart-activities!
           (fn [user-id cart-activities]
             (fn [callback]
               (let [activities (into [] (map (fn [[activity-id]]
                                                activity-id)
                                              @cart-activities))]
                 ; Add activity and get a new token from Stripe
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
              ; Get event logo
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
                  [:span [:i.red.remove.icon] "Remove"]
                  (fn [schedule-id _]
                    (api/schedules-endpoint :delete
                                            (d/entity @api/schedules-db
                                                      schedule-id)
                                            nil))
                  nil
                  mandatory-activities]]
                [:div.ui.vertical.segment
                 (if (seq event-activities)
                   [:div.ui.divided.items
                    (doall
                      (for [activity
                            (sort-by :StartTime
                                     (map #(d/entity @api/activities-db
                                                     (first %))
                                          (filter first
                                                  unscheduled-activities)))]
                        ^{:key (:ActivityId activity)}
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
                              [:i.right.chevron.icon]])]]]))]
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
              [logo/component @event-logo]]]))))
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:p "You are not registered for this event."]]]))

(routes/register-page routes/event-schedule-chan #'page true)
