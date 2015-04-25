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
  "Shows events for the given organization"
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [organization
            (d/entity @api/organizations-db organization-id)

            organization-events
            (->> (d/q '[:find [?event-id ...]
                        :in $ ?organization-id
                        :where
                        [?event-id :OrganizationId ?organization-id]]
                      @api/events-db
                      organization-id)
                 (map #(into {} (d/entity @api/events-db %)))
                 (map #(assoc %
                         :AttendeeId (-> '[:find [?attendee-id ...]
                                           :in $ ?user-id ?event-id
                                           :where
                                           [?attendee-id :UserId ?user-id]
                                           [?attendee-id :EventId ?event-id]]
                                         (d/q @api/attendees-db
                                              (get-in @state/session
                                                      [:user :UserId])
                                              (:EventId %))
                                         first)))
                 (sort-by :StartDate)
                 (filter #(when (seq %)
                            (re-find (re-pattern (str/lower-case @search))
                                     (str/lower-case (:Name %))))))

            paged-events
            (->> organization-events
                 (drop (* @page 10))
                 (take 10))

            ; Filter events by past/future
            organization-events-past
            (doall (filter #(after? (minus (now) (hours 6))
                                    (from-string (:EndDate %)))
                           paged-events))

            organization-events-future
            (doall (filter #(after? (from-string (:EndDate %)) (minus (now) (hours 6)))
                           paged-events))]
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
                        (if (:AttendeeId event)
                          [:a.ui.right.floated.small.button
                           {:href (routes/event-schedule event)}
                           "Your activities"
                           [:i.right.chevron.icon]]
                          (when (:token @state/session)
                            [:a.ui.right.floated.small.button
                             {:href (routes/event-register event)}
                             "Register"
                             [:i.right.chevron.icon]]))
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
                        (when (:AttendeeId event)
                          [:a.ui.right.floated.small.button
                           {:href (routes/event-schedule event)}
                           "Your activities"
                           [:i.right.chevron.icon]])
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
