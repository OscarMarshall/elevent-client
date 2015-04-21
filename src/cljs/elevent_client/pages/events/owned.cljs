(ns elevent-client.pages.events.owned
  (:require [clojure.string :as str]
            [reagent.core :refer [atom]]
            [datascript :as d]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.core :refer [after?]]
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
  (fn [callback]
    (api/events-endpoint :delete form #(api/permissions-endpoint ; Update permissions
                                         :read
                                         nil
                                         callback))))

(defn page []
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [owned-events
            (->> (get-in @state/session [:permissions :EventPermissions])
                 (filter (fn [[event-id event-permissions]]
                           (or (:EditEvent event-permissions)
                               (:EditUser  event-permissions))))
                 (map #(into {} (d/entity @api/events-db (first %))))
                 (sort-by :StartDate)
                 (filter #(when (seq %)
                            (re-find (re-pattern (str/lower-case @search))
                                     (str/lower-case (:Name %)))))
                 (drop (* @page 10))
                 (take 10)
                 doall)]
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
                    [event-details/component event]]
                   (when (get-in (:EventPermissions (:permissions @state/session))
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
                       (delete-event event)]])]])]
              [:p "No events found."])]
           [:div.ui.vertical.segment
            [paginator/component owned-events page]]]
          [:div.ui.dimmer {:class (when (empty? @api/events) :active)}
           [:div.ui.loader]]]]))))

(routes/register-page routes/events-owned-chan #'page true)
