(ns elevent-client.pages.events.id.activities.id.core
  (:require [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.components.activity-details :as activity-details]))

(defn page [event-id activity-id]
  (let [activity (d/entity @api/activities-db activity-id)]
    (when (seq activity)
      [:div.sixteen.wide.column
       [:div.ui.segment
        [:div.ui.vertical.segment
         [:h2.ui.dividing.header (:Name activity)]
         [activity-details/component activity]]]])))

(routes/register-page routes/event-activity-chan #'page)
