(ns elevent-client.pages.events.owned
  (:require [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.core :refer [after?]]
            [cljs-time.format :refer [unparse]]

            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.routes :as routes]
            [elevent-client.locale :as locale]))

(defn page []
  (let [created-events
        (doall (map #(into {} (d/entity @api/events-db %))
                    (d/q '[:find [?event-id ...]
                           :in $ ?user-id
                           :where
                           [?event-id :CreatorId  ?user-id]]
                         @api/events-db
                         (get-in @state/session [:user :UserId]))))]
    [:div.sixteen.wide.column
     [:div.ui.top.attached.tabular.menu
      [:a.item {:href (routes/events)}
       "Events"]
      [:a.item {:href (routes/events-explore)}
       "Explore"]
      [:a.active.item {:href (routes/events-owned)}
       "Owned"]
      [:a.item {:href (routes/event-add)}
       "Add"]]
     [:div.ui.bottom.attached.segment
      [:div
       [:div.ui.vertical.segment
        [:h1.ui.header "Events You Own"]]
       [:div.ui.vertical.segment
        (if (seq created-events)
          [:div.ui.divided.items
           (for [event created-events]
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
               [:div.extra
                [:a.ui.right.floated.small.button
                 {:href (routes/event-edit event)}
                 "Edit"
                 [:i.right.chevron.icon]]]]])]
          [:p "You don't own any events."])]]
      [:div.ui.dimmer {:class (when (empty? @api/events) :active)}
       [:div.ui.loader]]]]))

(routes/register-page routes/events-owned-chan #'page)
