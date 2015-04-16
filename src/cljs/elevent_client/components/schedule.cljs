(ns elevent-client.components.schedule
  (:require [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]
            [datascript :as d]

            [elevent-client.locale :as locale]
            [elevent-client.api :as api]))

(defn component
  [scheduled-activities & [button-text button-action footer-button]]
  [:table.ui.table
   [:thead
    [:tr
     [:th "Start"]
     [:th "End"]
     [:th "Activity"]
     [:th "Location"]
     [:th]]]
   [:tbody
    (for [[schedule-id activity-id] scheduled-activities]
      ^{:key schedule-id}
      (let [activity
            (when activity-id
              (d/entity @api/activities-db activity-id))]
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
          (when button-text
            [:div.ui.small.button
             {:on-click #(button-action schedule-id activity-id)}
             button-text])]]))]
   (when footer-button
     [:tfoot
      [:tr
       [:th {:colSpan "6"}
        footer-button
        #_[:div.ui.small.labeled.icon.button
         [:i.print.icon] "Print"]]]])])