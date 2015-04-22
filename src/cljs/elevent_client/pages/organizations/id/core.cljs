(ns elevent-client.pages.organizations.id.core
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [cljs-time.core :refer [after? now minus hours]]
            [cljs-time.coerce :refer [from-string]]

            [datascript :as d]

            [elevent-client.routes :as routes]
            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.components.event-details :as event-details]
            [elevent-client.components.input :as input]
            [elevent-client.components.paginator :as paginator]))

(defn page [organization-id]
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [organization
            (d/entity @api/organizations organization-id)
            
            organization-events
            (->> (d/q '[:find ?event-id
                        :in $ ?organization-id
                        :where
                        [?event-id :Organization-id ?organization-id]]
                      @api/events-db
                      organization-id)
                 (map (partial d/entity @api/events-db))
                 (sort-by :StartDate)
                 (filter #(when (seq %)
                            (re-find (re-pattern (str/lower-case @search))
                                     (str/lower-case (:Name %)))))
                 (drop (* @page 10))
                 (take 10)
                 doall)
            
            organization-events-past
            (doall (filter #(after? (minus (now) (hours 6)) (from-string (:EndDate %)))
                           organization-events))
            
            organization-events-future
            (doall (filter #(after? (from-string (:EndDate %)) (minus (now) (hours 6)))
                           organization-events))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:div
           [:div.ui.vertical.segment
            [:h1.ui.header (:Name organization) "'s Events"]]
           [:div.ui.vertical.segment
            [:div.ui.form
             [:div.field
              [:label "Search"]
              [input/component :text {} search]]]]
           (if (seq organization-events)
             [:div.ui.vertical.segment
              (when (seq organization-events-future)
                [:div.ui.vertical.segment
                 [:h2.ui.dividing.header "Upcoming"]
                 [:div.ui.divided.items
                  (for [event organization-events-future]
                    ^{:key (:EventId event)}
                    [:div.item
                     [:div.content
                      [:a.header {:href (routes/event event)}
                       (:Name event)]
                      [:div.meta
                       [event-details/component event]
                       [:div.extra
                        [:a.ui.right.floated.small.button
                         {:href (routes/event-schedule event)}
                         "Your activities"
                         [:i.right.chevron.icon]]
                        [:a.ui.right.floated.small.button
                         {:href (routes/event event)}
                         "Details"
                         [:i.right.chevron.icon]]]]]])]])
              (when (seq organization-events-past)
                [:div.ui.vertical.segment
                 [:h2.ui.dividing.header "Past"]
                 [:div.ui.divided.items
                  (for [event organization-events-past]
                    ^{:key (:EventId event)}
                    [:div.item
                     [:div.content
                      [:a.header {:href (routes/event event)}
                       (:Name event)]
                      [:div.meta
                       [event-details/component event]
                       [:div.extra
                        [:a.ui.right.floated.small.button
                         {:href (routes/event-schedule event)}
                         "Your activities"
                         [:i.right.chevron.icon]]
                        [:a.ui.right.floated.small.button
                         {:href (routes/event event)}
                         "Details"
                         [:i.right.chevron.icon]]]]]])]])]
              [:div.ui.vertical.segment
               [:p "No events found."]])
           [:div.ui.vertical.segment
            [paginator/component organization-events page]]]
          [:div.ui.dimmer {:class (when (empty? @api/events) "active")}
           [:div.ui.loader]]]]))))

(routes/register-page routes/organization-chan #'page)
