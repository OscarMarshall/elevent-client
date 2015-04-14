(ns elevent-client.pages.events.explore
  (:require [clojure.set :as set]
            [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.core :refer [after?]]
            [cljs-time.format :refer [ formatters unparse parse]]

            [elevent-client.state :as state]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]
            [elevent-client.api :as api]))

(defn page []
  (let [unattending-events
        (map (partial d/entity @api/events-db)
             (set/difference (into #{} (d/q '[:find [?event-id ...]
                                              :where [?event-id]]
                                            @api/events-db))
                             (into #{} (d/q '[:find [?event-id ...]
                                              :in $ ?user-id
                                              :where
                                              [?attendee-id :EventId ?event-id]
                                              [?attendee-id :UserId ?user-id]]
                                            @api/attendees-db
                                            (get-in @state/session
                                                    [:user :UserId])))))]
    [:div.sixteen.wide.column
     [:div.ui.top.attached.tabular.menu
      [:a.item {:href (routes/events)}
       "Events"]
      [:a.active.item {:href (routes/events-explore)}
       "Explore"]
      [:a.item {:href (routes/events-owned)}
       "Owned"]
      [:a.item {:href (routes/event-add)}
       "Add"]]
     [:div.ui.bottom.attached.segment
      [:div
       [:div.ui.vertical.segment
        [:h1.ui.header "Explore Events"]]
       [:div.ui.vertical.segment
        [:div.ui.divided.items
         (for [event (sort-by :StartDate unattending-events)]
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
                       (str " to " (unparse locale/datetime-formatter end)))))]
             [:div.meta
              [:strong "Venue:"]
              (:Venue event)]
             [:div.description
              (:Description event)]
             [:div.extra
              [:a.ui.right.floated.button
               {:href (routes/event-register event)}
               "Register"
               [:i.right.chevron.icon]]]]])]]]
      [:div.ui.dimmer {:class (when-not @api/events :active)}
       [:div.ui.loader]]]]))

(routes/register-page routes/events-explore-chan #'page)
