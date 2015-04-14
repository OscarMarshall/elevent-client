(ns elevent-client.pages.events.id.core
  (:require [datascript :as d]
            [ajax.core :refer [POST]]

            [elevent-client.api :as api]
            [elevent-client.config :as config]
            [elevent-client.state :as state]
            [elevent-client.routes :as routes]
            [elevent-client.components.event-details :as event-details]
            [elevent-client.components.schedule :as schedule]
            [elevent-client.components.activity-table :as activity-table]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.qr-code :as qr-code]))

(defn details-attendee [event leave-event]
  (when-let [attendee-id (first (d/q '[:find [?attendee-id ...]
                                       :in $ ?event-id ?user-id
                                       :where
                                       [?attendee-id :EventId ?event-id]
                                       [?attendee-id :UserId ?user-id]]
                                     @api/attendees-db
                                     (:EventId event)
                                     (:UserId (:user @state/session))))]
    (let [scheduled-activities
          (d/q '[:find ?schedule-id ?activity-id
                 :in $activities $schedules ?event-id ?user-id
                 :where
                 [$activities ?activity-id :EventId ?event-id]
                 [$schedules  ?schedule-id :UserId     ?user-id]
                 [$schedules  ?schedule-id :ActivityId ?activity-id]]
               @api/activities-db
               @api/schedules-db
               (:EventId event)
               (get-in @state/session [:user :UserId]))]
      [:div.sixteen.wide.column
       [:div.ui.segment
        [:div.ui.vertical.segment
         [:h1.ui.dividing.header
          (:Name event)]
         [event-details/component event]
         [action-button/component {:class "right floated small"}
          [:div
           [:i.red.remove.icon]
           "Leave event"]
          (leave-event attendee-id)]]
        [:div.ui.vertical.segment
         [:h2 "QR-Code"]
         [qr-code/component
          {:text (routes/event-attendee (into {} (d/entity @api/attendees-db
                                                          attendee-id)))}]]
        [:div.ui.vertical.segment
         [:h2 "Your Schedule"]
         [schedule/component scheduled-activities
          (list "Details" [:i.right.chevron.icon])
          (fn [_ activity-id]
            (js/location.replace (routes/event-activity
                                   {:EventId (:EventId event)
                                    :ActivityId activity-id})))
          [:a.ui.small.right.floated.labeled.icon.button
           {:href (routes/event-schedule event)}
           [:i.edit.icon]
           "Edit"]]]]])))

(defn details-owner [event activities attendees leave-event]
  (let [submit-image
        (fn [e]
          (.preventDefault e)
          (let [form-data
                (doto
                  (js/FormData.)
                  (.append "Logo"
                           (-> (js/jQuery "#file")
                               (aget 0)
                               (aget "files")
                               (aget 0))))]
            (POST (str config/http-url "/logos")
                  {:keywords?       true
                   :timeout         8000
                   :headers
                   (if (:token @state/session)
                     {:Authentication
                      (str "Bearer " (:token @state/session))}
                     {})
                   :params form-data
                   :handler #(prn "success!")
                   :error-handler #(prn "failed...")})))]
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:div.ui.vertical.segment
       [:h1.ui.dividing.header
        (:Name event)]
       [:div.ui.right.floated.small.labeled.icon.button
        [:i.edit.icon]
        "Edit"]
       [event-details/component event]]
      (when-let [attendee-id (first (d/q '[:find [?attendee-id ...]
                                           :in $ ?event-id ?user-id
                                           :where
                                           [?attendee-id :EventId ?event-id]
                                           [?attendee-id :UserId ?user-id]]
                                         @api/attendees-db
                                         (:EventId event)
                                         (:UserId (:user @state/session))))]
        [:div.ui.vertical.segment
         [:h2 "QR-Code"]
         [qr-code/component
          {:text (routes/event-attendee (into {} (d/entity @api/attendees-db
                                                          attendee-id)))}]])
      [:div.ui.vertical.segment
       [:h2 "Event Logo"]
       [:form.ui.form
        [:div.two.fields
         [:div.field
          [:label "Choose image"]
          [:input#file {:type "file"}]]
         [:div.field]]
        [:button.ui.primary.button
         {:type :submit
          :on-click submit-image}
         "Submit"]]]
      [:div.ui.vertical.segment
       [:h2.ui.header
        "Activities"]
       [activity-table/component (:EventId event)]]
      [:div.ui.vertical.segment
       [:h2.ui.header
        "Attendees"
        [:a.ui.right.floated.small.button
         {:href (routes/event-attendees event)}
         "View"]]
       [:table.ui.table
        [:thead
         [:tr
          [:th "Name"]
          [:th]]]
        [:tbody
         (for [attendee attendees]
           ^{:key (:AttendeeId attendee)}
           [:tr
            [:td (str (:FirstName attendee) " " (:LastName attendee))]
            [:td [:a.ui.right.floated.small.labeled.button
                  {:href (routes/event-attendee
                           {:EventId (:EventId event)
                            :AttendeeId (:AttendeeId attendee)})
                   :class (when (:CheckinTime attendee) :green)}
                  (if (:CheckinTime attendee)
                    "Checked in"
                    "Check in")]]])]
        [:tfoot
         [:tr
          [:th {:colSpan "4"}
           [:div.ui.right.floated.small.labeled.icon.button
            [:i.edit.icon]
            "Edit"]]]]]]]]))

(defn page [event-id]
  (let [event (into {} (d/entity @api/events-db event-id))

        activities (map #(d/entity @api/activities-db %)
                        (d/q '[:find [?e ...]
                               :in $ ?event-id
                               :where
                               [?e :EventId ?event-id]]
                             @api/activities-db
                             event-id))
        attendees (doall (take 10
                               (map (fn [[user-id attendee-id]]
                                      (merge
                                        (into
                                          {}
                                          (d/entity
                                            @api/users-db
                                            user-id))
                                        (into
                                          {}
                                          (d/entity
                                            @api/attendees-db
                                            attendee-id))))
                                    (d/q '[:find ?e ?a
                                           :in $ ?event-id
                                           :where
                                           [?a :EventId ?event-id]
                                           [?a :UserId ?e]]
                                         @api/attendees-db
                                         event-id))))
        leave-event (fn [attendee-id]
                      (fn [callback]
                        (api/attendees-endpoint
                          :delete
                          (d/entity @api/attendees-db attendee-id)
                          #(do
                             (callback)
                             (js/location.replace (routes/events))))))]
    (when (seq event)
      (cond
        ; TODO: if owner
        true
        [details-owner event activities attendees leave-event]

        ; TODO: if attendee
        true
        [details-attendee event leave-event]

        :else
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:div.ui.vertical.segment
           [:h1.ui.dividing.header
            (:Name event)]
           [event-details/component event]]]]))))

(routes/register-page routes/event-chan #'page)
