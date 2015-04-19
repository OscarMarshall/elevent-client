(ns elevent-client.pages.events.id.attendees.id.core
  (:require [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]
            [cljs-time.core :refer [after?]]
            [ajax.core :refer [PUT DELETE]]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.event-details :as event-details]
            [elevent-client.state :as state]
            [elevent-client.locale :as locale]
            [elevent-client.config :as config]))

(defn check-in-or-out [op url callback]
  (op (str config/https-url url)
      {:format :json
       :keywords? true
       :headers
       (if (:token @state/session)
         {:Authentication
          (str "Bearer " (:token @state/session))}
         {})
       :handler callback
       :error-handler
       (fn [] (swap! state/messages conj
                     [:error
                      "Check in failed. Please try again."]))}))

(defn check-in [attendee-id]
  (fn [callback]
    (check-in-or-out PUT
                     (str "/attendees/" attendee-id "/checkin")
                     (fn []
                       (api/attendees-endpoint :read nil callback)))))

(defn check-out [attendee-id]
  (fn [callback]
    (check-in-or-out DELETE
                     (str "/attendees/" attendee-id "/checkin")
                     (fn []
                       (api/attendees-endpoint :read nil callback)))))

(defn activity-check-in [schedule-id checked-in]
  (fn [callback]
    (check-in-or-out PUT
                     (str "/schedules/" schedule-id "/checkin")
                     (fn []
                       (api/schedules-endpoint :read nil callback)))))

(defn activity-check-out [schedule-id checked-in]
  (fn [callback]
    (check-in-or-out DELETE
                     (str "/schedules/" schedule-id "/checkin")
                     (fn []
                       (api/schedules-endpoint :read
                                               nil
                                               callback)))))

(defn page [event-id attendee-id]
  (let [event (into {} (d/entity @api/events-db event-id))

        attendee
        (into {} (first (map (fn [[user-id attendee-id]]
                               (merge (into {} (d/entity @api/users-db
                                                         user-id))
                                      (into {} (d/entity @api/attendees-db
                                                         attendee-id))))
                             (d/q '[:find ?e ?a
                                    :in $ ?attendee-id
                                    :where
                                    [?a :AttendeeId ?attendee-id]
                                    [?a :UserId ?e]]
                                  @api/attendees-db
                                  attendee-id))))

        attendee-activities
        (d/q '[:find ?schedule-id ?activity-id
               :in $activities $schedules $attendees ?event-id ?attendee-id
               :where
               [$activities ?activity-id :EventId ?event-id]
               [$attendees  ?a :AttendeeId ?attendee-id]
               [$attendees  ?a :UserId ?user-id]
               [$schedules  ?schedule-id :UserId     ?user-id]
               [$schedules  ?schedule-id :ActivityId ?activity-id]]
             @api/activities-db
             @api/schedules-db
             @api/attendees-db
             event-id
             attendee-id)]
    (if (and (seq event) (seq attendee))
      [:div.sixteen.wide.column
       [:div.ui.segment
        [:div
         [:div.ui.vertical.segment
          [:h1.ui.header
           (str (:FirstName attendee) " " (:LastName attendee))]]
         [:div.ui.vertical.segment
          [:div.ui.divided.items
           [:div.item
            [:div.content
             [:a.header
              {:href (routes/event event)}
              (:Name event)]
             [:div.meta
              [event-details/component event]]
             [:div.extra
              [action-button/component
               {:class "right floated"}
               (if (:CheckinTime attendee)
                 "Check out"
                 "Check in")
               (if (:CheckinTime attendee)
                 (check-out attendee-id)
                 (check-in attendee-id))
               (if (:CheckinTime attendee)
                 "Check in"
                 "Check out")]]]]]]
         [:div.ui.vertical.segment
          [:h3.ui.header
           "Attendee Info"]
          [:table.ui.definition.table.attendee-info
           [:tbody
            [:tr
             [:td "Email"]
             [:td (:Email attendee)]]]]]
         [:div.ui.vertical.segment
          [:h3.ui.header
           "Attendee Schedule"]
          [:table.ui.table
           [:thead
            [:tr
             [:th "Start"]
             [:th "End"]
             [:th "Activity"]
             [:th "Location"]
             [:th]]]
           [:tbody
            (for [[schedule-id activity-id] attendee-activities]
              ^{:key schedule-id}
              (let [activity
                    (when activity-id
                      (d/entity @api/activities-db activity-id))
                    schedule
                    (when schedule-id
                      (d/entity @api/schedules-db schedule-id))]
                (let
                  [checked-in (atom (not (nil? (:CheckinTime schedule))))]
                  [:tr
                   [:td {:noWrap true}
                    (when activity
                      (unparse locale/datetime-formatter
                               (from-string (:StartTime activity))))]
                   [:td {:noWrap true}
                    (when activity
                      (unparse locale/datetime-formatter
                               (from-string (:EndTime activity))))]
                   [:td (:Name activity)]
                   [:td (:Location activity)]
                   [:td.right.aligned {:noWrap true}
                    [action-button/component
                     {}
                     (if @checked-in
                       "Check out"
                       "Check in")
                     (if @checked-in
                       (activity-check-out schedule-id checked-in)
                       (activity-check-in schedule-id checked-in))
                     (if @checked-in
                       "Check in"
                       "Check out")]]])))]]]]]]
      [:div "Loading..."])))

(routes/register-page routes/event-attendee-chan #'page true)
