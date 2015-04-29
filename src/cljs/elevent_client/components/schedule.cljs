;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.components.schedule
  (:require [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]
            [cljs-time.core :refer [after?]]
            [datascript :as d]

            [elevent-client.components.help-icon :as help-icon]
            [elevent-client.locale :as locale]
            [elevent-client.api :as api]))

(defn component
  "Reusable table for attendee schedule
  Must provide the scheduled activities for the user
  Optional fields are for the action button and footer button"
  [scheduled-activities
   & [button-text button-action footer-button disabled-condition]]
  (let [scheduled-activities
        (->> scheduled-activities
             (map (fn [[schedule-id activity-id]]
                    (assoc (into {} (d/entity @api/activities-db activity-id))
                      :ScheduleId schedule-id)))
             (sort-by :StartTime))
        scheduled-activities
        (loop [out (into [] scheduled-activities) i 0]
          (cond
            (>= (inc i) (count scheduled-activities))
            out

            (after? (from-string (:EndTime (nth out i)))
                    (from-string (:StartTime (nth out (inc i)))))
            (recur (-> out
                       (assoc-in [i :Conflict] true)
                       (assoc-in [(inc i) :Conflict] true))
                   (inc i))

            :else
            (recur out (inc i))))]
    [:table.ui.table
     [:thead
      [:tr
       [:th "Start"]
       [:th "End"]
       [:th "Activity"]
       [:th "Location"]
       [:th]]]
     [:tbody
      (doall
        (for [activity (sort-by :StartTime scheduled-activities)]
          ^{:key (:ScheduleId activity)}
          [:tr {:class (when (:Conflict activity) "error")}
           [:td {:noWrap true}
            (when (:Conflict activity) [:a.ui.red.ribbon.label "Conflict"])
            (when activity
              [:p
               (unparse locale/time-formatter
                        (from-string (:StartTime activity)))
               [:br]
               (unparse locale/date-formatter
                        (from-string (:StartTime activity)))])]
           [:td {:noWrap true}
            (when activity
              [:p
               (unparse locale/time-formatter
                        (from-string (:EndTime activity)))
               [:br]
               (unparse locale/date-formatter
                        (from-string (:EndTime activity)))])]
           [:td (:Name activity)]
           [:td (:Location activity)]
           [:td.right.aligned {:noWrap true}
            (when button-text
              [:div
               ; Button shows help icon if required activity
               (when (and disabled-condition
                          (disabled-condition (:ActivityId activity)))
                 [help-icon/component "This is a required activity"])
               (when (not (and disabled-condition
                               (disabled-condition (:ActivityId activity))))
                 [:div.ui.small.button
                  {:on-click #(button-action (:ScheduleId activity)
                                             (:ActivityId activity))}
                  button-text])])]]))]
     ; Footer button exists when user has permissions to edit schedule
     (when footer-button
       [:tfoot
        [:tr
         [:th {:colSpan "6"}
          footer-button]]])]))
