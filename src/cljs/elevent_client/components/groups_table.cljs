(ns elevent-client.components.groups-table
  (:require [datascript :as d]

            [elevent-client.components.help-icon :as help-icon]
            [elevent-client.api :as api]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]))

(defn delete-group! [group]
  (when (js/window.confirm (str "Are you sure you want to delete "
                                (:Name group)
                                "?"))
    (api/groups-endpoint :delete
                         group
                         nil)))

(defn component [event-id]
  "Reusable table with groups for an event"
  (let [groups (doall (map #(into {} (d/entity @api/groups-db %))
                           (d/q '[:find [?group-id ...]
                                  :in $ ?event-id
                                  :where [?group-id :EventId ?event-id]]
                                @api/groups-db
                                    event-id)))]
    [:table.ui.table
     [:thead
      [:tr
       [:th "Name"]
       [:th "Members"]
       [:th "Actions"]]]
     [:tbody
      ; Get current event permissions
      (let [event-permissions (:EventPermissions (:permissions @state/session))]
        (doall (for [group groups]
                 ^{:key (:GroupId group)}
                 [:tr
                  [:td
                   ; Only let group name be a link if user has edit permissions
                   (if (get-in event-permissions
                               [event-id :EditEvent])
                     [:a {:href (routes/event-group (assoc group
                                                      :EventId event-id))}
                      (:Name group)]
                     (:Name group))]
                  [:td (count (d/q '[:find [?attendee-id ...]
                                     :in $ ?group-id
                                     :where
                                     [?attendee-id :GroupId ?group-id]]
                                   @api/attendees-db
                                   (:GroupId group)))]
                  ; Only know actions if user has edit permissions
                  (if (get-in event-permissions
                              [event-id :EditEvent])
                    [:td
                     [:a
                      {:href (routes/event-group {:EventId event-id
                                                  :GroupId (:GroupId group)})}
                      [help-icon/component "Add required activities" :i.plus.icon]]
                     [:a
                      {:href (routes/event-group-edit group)}
                      [:i.edit.icon]]
                     [:span
                      {:on-click #(delete-group! group)}
                      [:i.red.remove.icon.link]]]
                    [:td])])))]
     ; Check if user can edit groups
     (when (get-in (:EventPermissions (:permissions @state/session))
                   [event-id :EditEvent])
       [:tfoot
        [:tr
         [:th {:colSpan "6"}
          [:a.ui.small.right.floated.labeled.icon.button
           {:href (routes/event-group-add {:EventId event-id})}
           [:i.edit.icon]
           "Add"]]]])]))
