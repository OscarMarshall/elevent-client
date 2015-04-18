(ns elevent-client.pages.calendar
  (:require [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.calendar :as calendar]))

(defn page []
  (let [user-activities
        (d/q '[:find ?schedule-id ?activity-id
               :in $schedules $activities ?user-id
               :where
               [$schedules  ?schedule-id :UserId     ?user-id]
               [$schedules  ?schedule-id :ActivityId ?activity-id]
               [$activities ?activity-id :ActivityId ?activity-id]]
             @api/schedules-db
             @api/activities-db
             (get-in @state/session [:user :UserId]))

        user-events
        (d/q '[:find ?event-id
               :in $ ?user-id
               :where
               [?attendee-id :UserId ?user-id]
               [?attendee-id :EventId ?event-id]]
             @api/attendees-db
             (get-in @state/session [:user :UserId]))]
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:h1.ui.header
       "Calendar"]
      [calendar/component
       {:events (vec (concat
                       (map (fn [[schedule-id activity-id]]
                              (let [activity
                                    (when activity-id
                                      (d/entity @api/activities-db
                                                activity-id))]
                                {:title (:Name activity)
                                 :start (:StartTime activity)
                                 :end   (:EndTime activity)}))
                            user-activities)
                       (map (fn [[event-id]]
                              (let [event
                                    (when event-id
                                      (d/entity @api/events-db event-id))]
                                {:title (:Name event)
                                 :start (:StartDate event)
                                 :end   (:EndDate event)
                                 :color "#8fdf82"}))
                            user-events)))
                 :header {:left "title"
                          :center ""
                          :right "today prev,next month,agendaWeek,agendaDay"}
                 :defaultView "agendaWeek"}]]]))

(routes/register-page routes/calendar-chan #'page true)
