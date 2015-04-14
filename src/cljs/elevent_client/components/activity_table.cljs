(ns elevent-client.components.activity-table
  (:require [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]
            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]))

(defn component [event-id]
  (let [activities (doall (map #(into {} (d/entity @api/activities-db %))
                               (d/q '[:find [?activity-id ...]
                                      :in $ ?event-id
                                      :where [?activity-id :EventId ?event-id]]
                                    @api/activities-db
                                    event-id)))
        delete-activity! (fn [activity-id]
                           (api/activities-endpoint :delete
                                                    (d/entity @api/activities-db
                                                              activity-id)
                                                    nil))]
    [:table.ui.table
     [:thead
      [:tr
       [:th "Start Time"]
       [:th "End Time"]
       [:th "Activity"]
       [:th "Location"]
       [:th "Actions"]]]
     [:tbody
      (for [activity activities]
        ^{:key (:ActivityId activity)}
        [:tr
         [:td (let [start (from-string (:StartTime activity))]
                (unparse locale/datetime-formatter start))]
         [:td (let [end   (from-string (:EndTime   activity))]
                (unparse locale/datetime-formatter end))]
         [:td (:Name activity)]
         [:td (:Location activity)]
         [:td
          [:a
           {:href (routes/event-activity-edit activity)}
           [:i.edit.icon]]
          [:span
           {:style {:cursor "pointer"}
            :on-click #(delete-activity! (:ActivityId activity))}
           [:i.red.remove.icon]]]])]]))
