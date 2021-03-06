;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.components.activities-table
  (:require [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]
            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]))

(defn delete-activity! [activity-id]
  "Delete an activity from an event"
  (let [activity (d/entity @api/activities-db activity-id)]
    (when (js/window.confirm (str "Are you sure you want to delete "
                                  (:Name activity)
                                  "?"))
      (api/activities-endpoint :delete
                               activity
                               nil))))

(defn component [event-id]
  "Reusable table of all activities for the given event"
  (let [activities (doall (map #(into {} (d/entity @api/activities-db %))
                               (d/q '[:find [?activity-id ...]
                                      :in $ ?event-id
                                      :where [?activity-id :EventId ?event-id]]
                                    @api/activities-db
                                    event-id)))]
    [:table.ui.table
     [:thead
      [:tr
       [:th "Start Time"]
       [:th "End Time"]
       [:th "Activity"]
       [:th "Location"]
       [:th "Actions"]]]
     [:tbody
      ; Get session permissions
      (let [event-permissions (:EventPermissions (:permissions @state/session))]
        (for [activity (sort-by :StartTime activities)]
          ^{:key (:ActivityId activity)}
          [:tr
           [:td (let [start (from-string (:StartTime activity))]
                  (unparse locale/datetime-formatter start))]
           [:td (let [end   (from-string (:EndTime   activity))]
                  (unparse locale/datetime-formatter end))]
           [:td (:Name activity)]
           [:td (:Location activity)]
           ; Check if user can edit activities
           (if (get-in event-permissions
                       [event-id :EditEvent])
             [:td
              [:a
               {:href (routes/event-activity-edit activity)}
               [:i.edit.icon]]
              [:span
               {:on-click #(delete-activity! (:ActivityId activity))}
               [:i.red.remove.icon.link]]]
             [:td])]))]
     ; Only show add button if edit permissions
     (when (get-in (:EventPermissions (:permissions @state/session))
                   [event-id :EditEvent])
       [:tfoot
        [:tr
         [:th {:colSpan "6"}
          [:a.ui.small.right.floated.labeled.icon.button
           {:href (routes/event-activity-add {:EventId event-id})}
           [:i.edit.icon]
           "Add"]]]])]))
