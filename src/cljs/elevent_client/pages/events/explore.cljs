(ns elevent-client.pages.events.explore
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [reagent.core :refer [atom]]
            [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.core :refer [after?]]
            [cljs-time.format :refer [formatters unparse parse]]

            [elevent-client.state :as state]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]
            [elevent-client.api :as api]
            [elevent-client.components.input :as input]
            [elevent-client.components.paginator :as paginator]
            [elevent-client.pages.events.core :as events]))

(defn page []
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [unattending-events
            (->> (d/q '[:find [?event-id ...]
                        :in $ ?user-id
                        :where
                        [?attendee-id :EventId ?event-id]
                        [?attendee-id :UserId ?user-id]]
                      @api/attendees-db
                      (get-in @state/session [:user :UserId]))
                 (into #{})
                 (set/difference (into #{} (d/q '[:find [?event-id ...]
                                                  :where [?event-id]]
                                                @api/events-db)))
                 (map (partial d/entity @api/events-db))
                 (sort-by :StartDate)
                 (filter #(when (seq %)
                            (re-find (re-pattern (str/lower-case @search))
                                     (str/lower-case (:Name %)))))
                 (drop (* @page 10))
                 (take 10)
                 doall)]
        [:div.sixteen.wide.column
         [events/tabs :explore]
         [:div.ui.bottom.attached.segment
          [:div
           [:div.ui.vertical.segment
            [:h1.ui.header "Explore Events"]]
           [:div.ui.vertical.segment
            [:div.ui.form
             [:div.field
              [:label "Search"]
              [input/component :text {} search]]]]
           [:div.ui.vertical.segment
            (if (seq unattending-events)
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
                     [:i.right.chevron.icon]]
                    [:a.ui.right.floated.small.button
                     {:href (routes/event event)}
                     "Details"
                     [:i.right.chevron.icon]]]]])]
              [:p "No events found."])]
           [:div.ui.vertical.segment
            [paginator/component unattending-events page]]]
          [:div.ui.dimmer {:class (when-not @api/events :active)}
           [:div.ui.loader]]]]))))

(routes/register-page routes/events-explore-chan #'page)
