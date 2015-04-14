(ns elevent-client.pages.statistics
  (:require [reagent.core :refer [atom]]
            [datascript :as d]
            [cljs-time.coerce :refer [to-long]]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.components.chart :as chart]
            [elevent-client.components.input :as input]))

(defn page []
  (let [event-id (atom 0)]
    (fn []
      (let [events
            (d/q '[:find ?name ?id
                   :where [?id :Name ?name]]
                 @api/events-db)

            attendees
            (d/q '[:find [?check-in-time ...]
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
            (into (sorted-map) (frequencies (map to-long attendees)))

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
                         (d/q '[:find [?check-in-time ...]
                                :in $ ?activity-id
                                :where
                                [?schedule-id :ActivityId ?activity-id]
                                [?schedule-id :CheckinTime ?check-in-time]]
                              @api/schedules-db
                              activity-id))])
                    activities))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:h1.ui.header
           "Statistics"]
          [:div.ui.form
           [:div.two.fields
            [:div.field
             [:label "Choose event:"]
             [input/component :select {} events event-id identity int]]]]
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
              :yAxis {:title {:text "Number checked in"}}
              :series [{:name "Checked in"}]}
             check-in-data])
          (when (and (seq activities) (seq schedules))
            [chart/component
             {:chart {:type "bar"}
              :title {:text "Activity attendance"}
              ;:xAxis {:categories (map first schedules)}
              :yAxis {:title {:text "Number of attendees"}
                      :allowDecimals false}
              :series [{:name "Attendee count"}]}
             schedules])]]))))

(routes/register-page routes/statistics-chan #'page)
