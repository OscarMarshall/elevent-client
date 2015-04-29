;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.events.explore
  (:require [clojure.set :as set]
            [clojure.string :as str]
            [reagent.core :refer [atom]]
            [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.core :refer [after? now minus hours]]
            [cljs-time.format :refer [formatters unparse parse]]

            [elevent-client.state :as state]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]
            [elevent-client.api :as api]
            [elevent-client.components.input :as input]
            [elevent-client.components.event-details :as event-details]
            [elevent-client.components.paginator :as paginator]
            [elevent-client.pages.events.core :as events]))

(defn page []
  "Events the user is not registered for"
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [unattending-events ; future events user is not registered for
            (->>
             (d/q '[:find [?event-id ...]
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
             (filter
              #(when (seq %)
                (let [pattern (re-pattern (str/lower-case @search))]
                  (and (or (re-find pattern
                                    (str/lower-case (:Name %)))
                           (when (:Description %)
                             (re-find pattern
                                      (str/lower-case (:Description %)))))
                       (after? (from-string (:EndDate %))
                               (minus (now) (hours 6)))))))
             (sort-by :StartDate))
            paged-events
            (->> unattending-events
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
               (doall
                 (for [event paged-events]
                   ^{:key (:EventId event)}
                   [:div.item
                    [:div.content
                     [:a.header {:href (routes/event event)}
                      (:Name event)]
                     [:div.meta
                      [event-details/component event]]
                     [:div.extra
                      (when (:token @state/session)
                        [:a.ui.right.floated.small.button
                         {:href (routes/event-register event)}
                         "Register"
                         [:i.right.chevron.icon]])
                      [:a.ui.right.floated.small.button
                       {:href (routes/event event)}
                       "Details"
                       [:i.right.chevron.icon]]]]]))]
              [:p "No events found."])]
           [:div.ui.vertical.segment
            [paginator/component unattending-events page]]]
          [:div.ui.dimmer {:class (when-not @api/events :active)}
           [:div.ui.loader]]]]))))

(routes/register-page routes/events-explore-chan #'page)
