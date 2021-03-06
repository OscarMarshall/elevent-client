;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.events.owned
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.core :refer [after? now minus hours]]
            [cljs-time.format :refer [unparse]]

            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.routes :as routes]
            [elevent-client.locale :as locale]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.event-details :as event-details]
            [elevent-client.components.input :as input]
            [elevent-client.components.paginator :as paginator]
            [elevent-client.pages.events.core :as events]))

(defn delete-event [form]
  "Delete an event"
  (fn [callback]
    (if (js/window.confirm (str "Are you sure you want to delete "
                                (:Name form)
                                "?"))
      (api/events-endpoint :delete form #(api/permissions-endpoint ; Update permissions
                                                                   :read
                                                                   nil
                                                                   callback)
                           callback)
      (callback))))

(defn page []
  "Events the user has edit permissions on"
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [owned-events
            (->>
             (get-in @state/session [:permissions :EventPermissions])
             (filter (fn [[event-id event-permissions]]
                       (or (:EditEvent event-permissions)
                           (:EditUser  event-permissions))))
             (map #(into {} (d/entity @api/events-db (first %))))
             (filter #(when (seq %)
                       (let [pattern (re-pattern (str/lower-case @search))]
                         (or (re-find pattern
                                      (str/lower-case (:Name %)))
                             (when (:Description %)
                               (re-find pattern
                                        (str/lower-case (:Description %))))))))
             (sort-by :StartDate))
            paged-events
            (->> owned-events
                 (drop (* @page 10))
                 (take 10))
            ; Split by past and future events
            owned-events-past
            (doall (filter #(after? (minus (now) (hours 6)) (from-string (:EndDate %)))
                           paged-events))
            owned-events-future
            (doall (filter #(after? (from-string (:EndDate %)) (minus (now) (hours 6)))
                           paged-events))

            event-permissions
            (:EventPermissions (:permissions @state/session))]
        [:div.sixteen.wide.column
         [events/tabs :owned]
         [:div.ui.bottom.attached.segment
          [:div
           [:div.ui.vertical.segment
            [:h1.ui.header "Events You Own"]]
           [:div.ui.vertical.segment
            [:div.ui.form
             [:div.field
              [:label "Search"]
              [input/component :text {} search]]]]
           (if (seq owned-events)
             [:div.ui.vertical.segment
              (when (seq owned-events-future)
                [:div.ui.vertical.segment
                 [:h2.ui.dividing.header "Upcoming"]
                 [:div.ui.divided.items
                  (doall
                    (for [event owned-events-future]
                      ^{:key (:EventId event)}
                      [:div.item
                       [:div.content
                        [:a.header {:href (routes/event event)}
                         (:Name event)]
                        [:div.meta
                         [event-details/component event]]
                        [:div.extra
                         (when (get-in event-permissions
                                       [(:EventId event) :EditEvent])
                           [:a.ui.right.floated.small.button
                            {:href (routes/event-edit event)}
                            "Edit"
                            [:i.right.chevron.icon]])
                         [:a.ui.right.floated.small.button
                          {:href (routes/event event)}
                          "Details"
                          [:i.right.chevron.icon]]
                         (when (get-in (:EventPermissions (:permissions @state/session))
                                       [(:EventId event) :EditEvent])
                           [action-button/component
                            {:class "ui right floated small negative"}
                            "Delete"
                            (delete-event event)])]]]))]])
              (when (seq owned-events-past)
                [:div.ui.vertical.segment
                 [:h2.ui.dividing.header "Past"]
                 [:div.ui.divided.items
                  (for [event owned-events-past]
                    ^{:key (:EventId event)}
                    [:div.item
                     [:div.content
                      [:a.header {:href (routes/event event)}
                       (:Name event)]
                      [:div.meta
                       [event-details/component event]]
                      (when (get-in event-permissions
                                    [(:EventId event) :EditEvent])
                        [:div.extra
                         [:a.ui.right.floated.small.button
                          {:href (routes/event-edit event)}
                          "Edit"
                          [:i.right.chevron.icon]]
                         [:a.ui.right.floated.small.button
                          {:href (routes/event event)}
                          "Details"
                          [:i.right.chevron.icon]]
                         [action-button/component
                          {:class "ui right floated small negative"}
                          "Delete"
                          (delete-event event)]])]])]])]
             [:div.ui.vertical.segment
              [:p "No events found."]])
           [:div.ui.vertical.segment
            [paginator/component owned-events page]]]
          [:div.ui.dimmer {:class (when (empty? @api/events) :active)}
           [:div.ui.loader]]]]))))

(routes/register-page routes/events-owned-chan #'page true)
