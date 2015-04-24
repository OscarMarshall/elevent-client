(ns elevent-client.components.schedule
  (:require [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]
            [datascript :as d]

            [elevent-client.components.help-icon :as help-icon]
            [elevent-client.locale :as locale]
            [elevent-client.api :as api]))

(defn component
  "Reusable table for attendee schedule
   Must provide the scheduled activities for the user
   Optional fields are for the action button and footer button"
  [scheduled-activities & [button-text button-action footer-button disabled-condition]]
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
      (for [[schedule-id activity-id] scheduled-activities]
        ^{:key schedule-id}
        (let [activity
              (when activity-id
                (d/entity @api/activities-db activity-id))]
          [:tr
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
               ; Button shows help icon if required activity
               (when (and disabled-condition (disabled-condition activity-id))
                 [help-icon/component "This is a required activity"])
               [:div.ui.small.button
                {:class (when (and disabled-condition (disabled-condition activity-id)) "disabled")
                 :on-click #(button-action schedule-id activity-id)}
                button-text]])]])))]
   ; Footer button exists when user has permissions to edit schedule
   (when footer-button
     [:tfoot
      [:tr
       [:th {:colSpan "6"}
        footer-button]]])])
