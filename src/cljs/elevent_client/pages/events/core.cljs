(ns elevent-client.pages.events.core
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

(defn tabs [page]
  (let [logged-in? (:token @state/session)]
    [:div.ui.top.attached.tabular.menu
     (when logged-in?
       [:a.item {:href (routes/events) :class (when (= page :core)
                                                "active")}
        "Events"])
     [:a.item {:href (routes/events-explore) :class (when (= page :explore)
                                                      "active")}
      "Explore"]
     (when logged-in?
       [:a.item {:href (routes/events-owned) :class (when (= page :owned)
                                                      "active")}
        "Owned"])
     (when logged-in?
       [:a.item {:href (routes/event-add) :class (when (= page :add)
                                                   "active")}
        "Add"])]))

(defn page []
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [attending-events
            (->> (d/q '[:find ?event-id ?attendee-id
                        :in $ ?user-id
                        :where
                        [?attendee-id :UserId  ?user-id]
                        [?attendee-id :EventId ?event-id]]
                      @api/attendees-db
                      (get-in @state/session [:user :UserId]))
                 (map (fn [[event-id attendee-id]]
                        (assoc (into {} (d/entity @api/events-db event-id))
                          :AttendeeId attendee-id)))
                 (sort-by :StartDate)
                 (filter #(when (:Name %)
                            (re-find (re-pattern (str/lower-case @search))
                                     (str/lower-case (:Name %))))))
            paged-events
            (->> attending-events
                 (drop (* @page 10))
                 (take 10))
            attending-events-past
            (doall (filter #(after? (minus (now) (hours 6))
                                    (from-string (:EndDate %)))
                           paged-events))
            attending-events-future
            (doall (filter #(after? (from-string (:EndDate %)) (minus (now) (hours 6)))
                           paged-events))]
        [:div.sixteen.wide.column
         [tabs :core]
         [:div.ui.bottom.attached.segment
          [:div
           [:div.ui.vertical.segment
            [:h1.ui.header "Events You're Attending"]]
           [:div.ui.vertical.segment
            [:div.ui.form
             [:div.field
              [:label "Search"]
              [input/component :text {} search]]]]
           (if (seq attending-events)
             [:div.ui.vertical.segment
              (when (seq attending-events-future)
                [:div.ui.vertical.segment
                 [:h2.ui.dividing.header "Upcoming"]
                 [:div.ui.divided.items
                  (for [event attending-events-future]
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
              (when (seq attending-events-past)
                [:div.ui.vertical.segment
                 [:h2.ui.dividing.header "Past"]
                 [:div.ui.divided.items
                  (for [event attending-events-past]
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
            [paginator/component attending-events page]]]
          [:div.ui.dimmer {:class (when (empty? @api/events) "active")}
           [:div.ui.loader]]]]))))

(routes/register-page routes/events-chan #'page true)
