(ns elevent-client.components.groups-table
  (:require [datascript :as d]

            [elevent-client.components.help-icon :as help-icon]
            [elevent-client.api :as api]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]))

(defn component [event-id]
  (let [groups (doall (map #(into {} (d/entity @api/groups-db %))
                           (d/q '[:find [?group-id ...]
                                  :in $ ?event-id
                                  :where [?group-id :EventId ?event-id]]
                                @api/groups-db
                                    event-id)))
        delete-group! (fn [group]
                        (api/groups-endpoint :delete
                                             group
                                             nil))]
    [:table.ui.table
     [:thead
      [:tr
       [:th "Name"]
       [:th "Members"]
       [:th "Actions"]]]
     [:tbody
      (let [event-permissions (:EventPermissions (:permissions @state/session))]
        (doall (for [group groups]
                 ^{:key (:GroupId group)}
                 [:tr
                  [:td
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
