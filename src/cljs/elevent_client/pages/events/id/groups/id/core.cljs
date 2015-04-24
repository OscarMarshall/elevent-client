(ns elevent-client.pages.events.id.groups.id.core
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :refer [put!]]
            [ajax.core :refer [PUT]]
            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.config :as config]
            [elevent-client.authentication :as auth]
            [elevent-client.state :as state]
            [elevent-client.routes :as routes]
            [elevent-client.components.input :as input]
            [elevent-client.components.action-button :as action-button]))

(defn delete-mandate! [mandate-id]
  (let [mandate (d/entity @api/mandates-db mandate-id)
        group (d/entity @api/groups-db (:GroupId mandate))
        activity (d/entity @api/activities-db (:ActivityId mandate))]
    (when (js/window.confirm (str "Are you sure that you want to delete the "
                                  (:Name activity)
                                  " mandate for "
                                  (:Name group)
                                  "?"))
    (api/mandates-endpoint :delete mandate nil))))

(defn page [event-id group-id]
  (let [form (atom {})]
    (fn [event-id group-id]
      (let [group
            (d/entity @api/groups-db group-id)

            mandates
            (doall (map #(let [mandate (d/entity @api/mandates-db %)]
                           (merge (into {} (d/entity @api/activities-db
                                                     (:ActivityId mandate)))
                                  mandate))
                        (d/q '[:find [?mandate-id ...]
                               :in $ ?group-id
                               :where [?mandate-id :GroupId ?group-id]]
                             @api/mandates-db
                             group-id)))
            activities
            (d/q '[:find ?name ?activity-id
                   :in $ ?event-id
                   :where
                   [?activity-id :EventId ?event-id]
                   [?activity-id :Name ?name]]
                 @api/activities-db
                 event-id)

            groups
            (d/q '[:find ?name ?group-id
                   :in $ ?event-id
                   :where
                   [?group-id :EventId ?event-id]
                   [?group-id :Name ?name]]
                 @api/groups-db
                 event-id)

            attendees
            (doall (map (fn [[user-id attendee-id]]
                          (assoc (into {} (d/entity @api/users-db
                                                    user-id))
                            :AttendeeId attendee-id
                            :GroupId group-id))
                        (d/q '[:find ?user-id ?attendee-id
                               :in $ ?event-id ?group-id
                               :where
                               [?attendee-id :EventId ?event-id]
                               [?attendee-id :GroupId ?group-id]
                               [?attendee-id :UserId ?user-id]]
                             @api/attendees-db
                             (:EventId group)
                             (:GroupId group))))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:div.ui.vertical.segment
           [:h1.ui.header (:Name group)
            [:a.ui.small.right.floated.button
             {:href (routes/event-attendees {:EventId event-id})}
             "Add Attendees"
             [:i.right.chevron.icon]]]]
          [:div.ui.vertical.segment
           [:h2.ui.header "Required Activities"]
           [:div.ui.form
            [:div.inline.field
             [input/component
              :select
              {}
              activities
              (r/wrap (:ActivityId @form) swap! form assoc :ActivityId)]
             [action-button/component
              {:class (when-not (:ActivityId @form) :disabled)}
              "Add"
              (fn [callback]
                (if (:ActivityId @form)
                  (api/mandates-endpoint
                    :create
                    (assoc @form :GroupId group-id)
                    (fn [] (api/schedules-endpoint :read nil #(callback)))
                    callback)
                  (callback)))]]]
           [:table.ui.table
            [:thead
             [:tr
              [:th "Activity"]
              [:th "Actions"]]]
            [:tbody
             (for [mandate mandates]
               ^{:key {:MandateId mandate}}
               [:tr
                [:td (:Name mandate)]
                [:td
                 [:span
                  {:style {:cursor "pointer"}
                   :on-click #(delete-mandate! (:MandateId mandate))}
                  [:i.red.remove.icon]]]])]]]
          [:div.ui.vertical.segment
           [:h2.ui.header "Attendees"]
           [:table.ui.table
            [:thead
             [:tr
              [:th "Email"]
              [:th "Name"]
              [:th "Group"]]]
            [:tbody
             (for [attendee attendees]
               ^{:key {:AttendeeId attendee}}
               [:tr
                [:td (:Email attendee)]
                [:td (:FirstName attendee) " " (:LastName attendee)]
                [:td
                 [input/component
                  :select
                  {}
                  (cons ["None" 0] groups)
                  (r/wrap
                    (:GroupId attendee)
                    (fn [x]
                      (let [uri (str config/https-url
                                     "/attendees/"
                                     (:AttendeeId attendee)
                                     "/groups/"
                                     x)]
                        (PUT
                          uri
                          {:timeout
                           8000

                           :headers
                           {:Authentication
                            (str "Bearer " (:token @state/session))}

                           :handler
                           #(api/attendees-endpoint :read nil nil nil)

                           :error-handler
                           (fn [error]
                             (if (= (:status error) 401)
                               (when (:token @state/session)
                                 (put! state/add-messages-chan
                                       [:logged-out
                                        [:negative "Logged out"]])
                                 (auth/sign-out!))
                               (if (= (:failure error) :timeout)
                                 (put! state/add-messages-chan
                                       [(keyword "elevent-client.api"
                                                 (str uri "-timed-out"))
                                        [:negative (str uri " timed out")]])
                                 (put! state/add-messages-chan
                                       [(keyword "elevent-client.api" (gensym))
                                        [:negative
                                         (str uri (js->clj error))]]))))}))))]]])]]]]]))))

(routes/register-page routes/event-group-chan #'page)
