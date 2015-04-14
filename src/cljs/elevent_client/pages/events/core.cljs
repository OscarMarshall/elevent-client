(ns elevent-client.pages.events.core
  (:require [datascript :as d]

            [elevent-client.routes :as routes]
            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.components.event-details :as event-details]))

(defn page []
  (fn []
    (if (:token @state/session)
      (let [attending-events
            (doall (map (fn [[event-id attendee-id]]
                          (merge
                            (into {} (d/entity @api/events-db event-id))
                            {:AttendeeId attendee-id}))
                        (d/q '[:find ?event-id ?attendee-id
                               :in $ ?user-id
                               :where
                               [?attendee-id :UserId  ?user-id]
                               [?attendee-id :EventId ?event-id]]
                             @api/attendees-db
                             (get-in @state/session [:user :UserId]))))]
        [:div.sixteen.wide.column
         [:div.ui.top.attached.tabular.menu
          [:a.active.item {:href (routes/events)}
           "Events"]
          [:a.item {:href (routes/events-explore)}
           "Explore"]
          [:a.item {:href (routes/events-owned)}
           "Owned"]
          [:a.item {:href (routes/event-add)}
           "Add"]]
         [:div.ui.bottom.attached.segment
          [:div
           [:div.ui.vertical.segment
            [:h1.ui.header "Events You're Attending"]]
           [:div.ui.vertical.segment
            (if (seq attending-events)
              [:div.ui.divided.items
               (for [event attending-events]
                 ^{:key (:EventId event)}
                 [:div.item
                  [:div.content
                   [:a.header {:href (routes/event event)}
                    (:Name event)]
                   [:div.meta
                    [event-details/component event]
                   [:div.extra
                    [:a.ui.right.floated.small.button {:href (routes/event-schedule event)}
                     "Your activities"
                     [:i.right.chevron.icon]]]]]])]
              [:p "You aren't attending any events."])]]
          [:div.ui.dimmer {:class (when (empty? @api/events) "active")}
           [:div.ui.loader]]]])
      (do
        (js/location.replace (routes/events-explore))
        [:div]))))

(routes/register-page routes/events-chan #'page)
