(ns elevent-client.components.schedule
  (:require [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]
            [cljs-time.core :refer [after?]]
            [datascript :as d]

            [elevent-client.components.help-icon :as help-icon]
            [elevent-client.locale :as locale]
            [elevent-client.api :as api]))

(defn component [scheduled-activities
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
        (for [activity scheduled-activities]
          ^{:key (:ScheduleId activity)}
          [:tr {:class (when (:Conflict activity) "error")}
           [:td {:noWrap true}
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
               (when (and disabled-condition
                          (disabled-condition (:ActivityId activity)))
                 [help-icon/component "This is a required activity"])
               [:div.ui.small.button
                {:class (when (and disabled-condition
                                   (disabled-condition (:ActivityId activity)))
                          "disabled")
                 :on-click #(button-action (:ScheduleId activity)
                                           (:ActivityId activity))}
                (when (:Conflict activity)
                  [:span [:span.ui.red.label "CONFLICT"] " "])
                button-text]])]]))]
     (when footer-button
       [:tfoot
        [:tr
         [:th {:colSpan "6"}
          footer-button
          #_[:div.ui.small.labeled.icon.button
             [:i.print.icon] "Print"]]]])]))
