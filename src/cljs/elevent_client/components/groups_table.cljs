(ns elevent-client.components.groups-table
  (:require [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]))

(defn component [event-id]
  (let [groups (doall (map #(into {} (d/entity @api/groups-db %))
                           (d/q '[:find [?group-id ...]
                                  :in $ ?event-id
                                  :where [?group-id :EventId ?event-id]]
                                @api/groups-db
                                    event-id)))
        delete-group! (fn [group-id]
                        (api/groups-endpoint :delete
                                             (d/entity @api/groups-db
                                                       group-id)
                                             nil))]
    [:table.ui.table
     [:thead
      [:tr
       [:th "Name"]
       [:th "Members"]
       [:th "Actions"]]]
     [:tbody
      (doall (for [group groups]
               ^{:key (:GroupId group)}
               [:tr
                [:td
                 [:a {:href (routes/event-group (assoc group
                                                  :EventId event-id))}
                      (:Name group)]]
                [:td (count (d/q '[:find [?attendee-id ...]
                                   :in $ ?group-id
                                   :where
                                   [?attendee-id :GroupId ?group-id]]
                                 @api/attendees-db
                                 (:GroupId group)))]
                [:td
                 [:a
                  {:href (routes/event-group-edit group)}
                  [:i.edit.icon]]
                 [:span
                  {:on-click #(delete-group! (:GroupId group))}
                  [:i.red.remove.icon.link]]]]))]
     [:tfoot
      [:tr
       [:th {:colSpan "6"}
        [:a.ui.small.right.floated.labeled.icon.button
         {:href (routes/event-group-add {:EventId event-id})}
         [:i.edit.icon]
         "Edit"]]]]]))
