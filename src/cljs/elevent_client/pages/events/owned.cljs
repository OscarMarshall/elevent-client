(ns elevent-client.pages.events.owned
  (:require [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.core :refer [after?]]
            [cljs-time.format :refer [unparse]]

            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.routes :as routes]
            [elevent-client.locale :as locale]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.pages.events.core :as events]))

(defn delete-event [form]
  (fn [callback]
    (api/events-endpoint :delete form #(callback))))

(defn page []
  (let [owned-events
        (doall (map #(into {} (d/entity @api/events-db %))
                    (keys
                      (into {}
                            (filter
                              (fn [[event-id event-permissions]]
                                (or (:EditEvent event-permissions)
                                    (:EditUser  event-permissions)))
                              (:EventPermissions (:permissions @state/session)))))))]
    [:div.sixteen.wide.column
     [events/tabs :owned]
     [:div.ui.bottom.attached.segment
      [:div
       [:div.ui.vertical.segment
        [:h1.ui.header "Events You Own"]]
       [:div.ui.vertical.segment
        (if (seq owned-events)
          [:div.ui.divided.items
           (for [event owned-events]
             ^{:key (:EventId event)}
             [:div.item
              [:div.content
               [:a.header {:href (routes/event event)}
                (:Name event)]
               [:div.meta
                [:strong "Date:"]
                (let [start (from-string (:StartDate event))
                      end   (from-string (:EndDate   event))]
                  (str (unparse locale/datetime-formatter start)
                       (when (after? end start)
                         (str " to "
                              (unparse locale/datetime-formatter end)))))]
               [:div.meta
                [:strong "Venue:"]
                (:Venue event)]
               [:div.description
                (:Description event)]
               (when (get-in (:EventPermissions (:permissions @state/session))
                             [(:EventId event) :EditEvent])
                 [:div.extra
                  [:a.ui.right.floated.small.button
                   {:href (routes/event-edit event)}
                   "Edit"
                   [:i.right.chevron.icon]]
                  [action-button/component
                   {:class "ui right floated small negative"}
                   "Delete"
                   (delete-event event)]])]])]
          [:p "You don't own any events."])]]
      [:div.ui.dimmer {:class (when (empty? @api/events) :active)}
       [:div.ui.loader]]]]))

(routes/register-page routes/events-owned-chan #'page true)
