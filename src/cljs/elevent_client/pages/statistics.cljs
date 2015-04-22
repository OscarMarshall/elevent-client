(ns elevent-client.pages.statistics
  (:require [reagent.core :refer [atom]]
            [datascript :as d]
            [cljs-time.coerce :refer [to-long]]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.chart :as chart]
            [elevent-client.components.input :as input]))

(defn page []
  (let [event-id (atom 0)]
    (fn []
      (let [events
            (doall
              (filter (fn [[event-name event-id]]
                        (get-in (:EventPermissions (:permissions @state/session))
                                [event-id :EditEvent]))
                      (d/q '[:find ?name ?id
                             :where [?id :Name ?name]]
                           @api/events-db)))

            attendees
            (d/q '[:find ?attendee-id ?check-in-time
                   :in $ ?event-id
                   :where
                   [?attendee-id :EventId ?event-id]
                   [?attendee-id :CheckinTime ?check-in-time]]
                 @api/attendees-db
                 @event-id)

            all-attendees
            (d/q '[:find [?attendee-id ...]
                   :in $ ?event-id
                   :where
                   [?attendee-id :EventId ?event-id]]
                 @api/attendees-db
                 @event-id)

            check-in-data
            (into (sorted-map) (frequencies (map (comp to-long second) attendees)))

            check-in-data
            (sort-by first (vec (zipmap (keys check-in-data)
                                        (reduce #(conj %1 (+ (last %1) %2))
                                                []
                                                (vals check-in-data)))))

            activities
            (d/q '[:find ?name ?id
                   :in $ ?event-id
                   :where
                   [?id :Name ?name]
                   [?id :EventId ?event-id]]
                 @api/activities-db
                 @event-id)

            schedules
            (into []
                  (map
                    (fn [[activity-name activity-id]]
                      [activity-name
                       (count
                         (d/q '[:find ?schedule-id ?check-in-time
                                :in $ ?activity-id
                                :where
                                [?schedule-id :ActivityId ?activity-id]
                                [?schedule-id :CheckinTime ?check-in-time]]
                              @api/schedules-db
                              activity-id))])
                    activities))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:div.ui.vertical.segment
           [:h1.ui.dividing.header
            "Statistics"]
           [:div.ui.form
            [:div.one.field
             [:div.eight.wide.field
              [:label "Choose event:"]
              [input/component :select {} events event-id identity int]]]]]
          [:div.ui.vertical.segment
           (when (seq all-attendees)
             [chart/component
              {:chart
               {:type "pie"}

               :title
               {:text "Attendance"}

               :tooltip
               {:pointFormat "{point.name}: <b>{point.percentage:.1f}%</b>"}

               :plotOptions
               {:pie {:cursor "pointer"
                      :dataLabels {:enabled true
                                   :format "<b>{point.name}</b>: {point.y}"}}}

               :series
               []}
              [["Attended" (count attendees)]
               ["Did Not Attend" (- (count all-attendees)
                                    (count attendees))]]])
           (when (seq check-in-data)
             [chart/component
              {:chart {:type "line"}
               :title {:text "Check in Times"}
               :xAxis {:type "datetime"
                       :dateTimeLabelFormats {:day "%e %b"}}
               :yAxis {:title {:text "Number checked in"}
                       :min 0}
               :series [{:name "Checked in"}]}
              check-in-data])
           ; Check if there is any data to display
           (when (reduce #(or %1 %2)
                         (map
                           (fn [[activity-name attendance-count]]
                             (> attendance-count 0))
                           schedules))
             [chart/component
              {:chart {:type "bar"}
               :title {:text "Activity attendance"}
               :xAxis {:min 0}
               :yAxis {:title {:text "Number of attendees"}
                       :min 0
                       :minRange 1
                       :allowDecimals false}
               :series [{:name "Attendee count"}]}
              schedules])
           (when (and (> @event-id 0)
                      (not (seq all-attendees)))
             [:p "There is no data for this event."])]]]))))

(routes/register-page routes/statistics-chan #'page true)
