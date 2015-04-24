(ns elevent-client.pages.events.id.core
  (:require [datascript :as d]
            [cljs.core.async :as async :refer [put!]]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [POST DELETE]]

            [elevent-client.api :as api]
            [elevent-client.config :as config]
            [elevent-client.state :as state]
            [elevent-client.routes :as routes]
            [elevent-client.components.event-details :as event-details]
            [elevent-client.components.schedule :as schedule]
            [elevent-client.components.activities-table :as activities-table]
            [elevent-client.components.activity-details :as activity-details]
            [elevent-client.components.groups-table :as groups-table]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.logo :as logo]
            [elevent-client.components.qr-code :as qr-code]
            [elevent-client.pages.events.id.attendees.id.core :refer [check-in]]))

(defn details-attendee [event leave-event event-logo]
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
       [:div.ui.sixteen.column.grid
        [:div.column {:class (str (if (:HasLogo event)
                                    "thirteen"
                                    "sixteen")
                                  " wide")}
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
            {:text (str config/site-url
                        (routes/event-attendee (into {} (d/entity @api/attendees-db
                                                                  attendee-id))))}]]
          [:div.ui.vertical.segment
           [:h2 "Your Schedule"]
           [schedule/component scheduled-activities
            (list "Details" [:i.right.chevron.icon])
            (fn [_ activity-id]
              (js/location.assign (routes/event-activity
                                    {:EventId (:EventId event)
                                     :ActivityId activity-id})))
            [:a.ui.small.right.floated.labeled.icon.button
             {:href (routes/event-schedule event)}
             [:i.edit.icon]
             "Edit"]]]]]
        [logo/component @event-logo]]])))

(defn details-owner [event activities attendees leave-event
                     logo-to-upload event-logo image-error get-logo
                     can-edit can-check-in is-attendee]
  (let [submit-image
        (fn [callback]
          (let [file (-> (js/jQuery "#file")
                         (aget 0)
                         (aget "files")
                         (aget 0))
                form-data
                (doto
                  (js/FormData.)
                  (.append "Logo" file))]
            (POST (str config/http-url "/events/" (:EventId event) "/logos")
                  {:keywords?       true
                   :timeout         8000
                   :headers
                   (if (:token @state/session)
                     {:Authentication
                      (str "Bearer " (:token @state/session))}
                     {})
                   :params form-data
                   :handler (fn []
                              (callback)
                              (api/events-endpoint :read nil nil)
                              #_(get-logo))
                   :error-handler
                   (fn [_]
                     (callback)
                     (put! state/add-messages-chan
                           [:logo-upload-failed
                            [:negative "Upload failed. Please try again."]]))})))
        delete-image
        (fn [callback]
          (if (js/window.confirm
                "Are you sure you want to remove the event logo?")
            (DELETE (str config/http-url "/events/" (:EventId event) "/logos")
                    {:keywords?       true
                     :timeout         8000
                     :headers
                     (if (:token @state/session)
                       {:Authentication
                        (str "Bearer " (:token @state/session))}
                       {})
                     :handler (fn []
                                (reset! event-logo nil)
                                (callback)
                                (api/events-endpoint :read nil nil))
                     :error-handler
                     (fn [_]
                       (callback)
                       (put! state/add-messages-chan
                             [:logo-delete-failed
                              [:negative "Remove failed. Please try again."]]))})
            (callback)))
        file-changed
        (fn []
          (let [file (-> (js/jQuery "#file")
                         (aget 0)
                         (aget "files")
                         (aget 0))]
            (reset! logo-to-upload (not (= (.val (js/jQuery "#file")) "")))
                    (if (> (aget file "size") 1000000) ; image is too large
                      (reset! image-error "Image is too large. Must be under 1 MB.")
                      (reset! image-error nil))))
        scheduled-activities
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
       (when can-edit
         [:a.ui.right.floated.small.labeled.icon.button
          {:href (routes/event-edit event)}
          [:i.edit.icon]
          "Edit"])
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
         [action-button/component {:class "right floated small"}
          [:div
           [:i.red.remove.icon]
           "Leave event"]
          (leave-event attendee-id)]
         [qr-code/component
          {:text (str config/site-url
                      (routes/event-attendee (into {} (d/entity @api/attendees-db
                                                                attendee-id))))}]])
      (when can-edit
        [:div.ui.vertical.segment
         [:h2 "Event Logo"]
         (when (and @state/online? @event-logo)
           [:img {:style {:height "100px"} :src @event-logo}])
         [:form.ui.form
          [:div.two.fields
           [:div.field
            [:label "Choose image"]
            [:input#file {:type "file"
                          :on-change file-changed}]
            (when @image-error [:div.ui.red.pointing.prompt.label @image-error])]
           [:div.field]]
          [action-button/component
           {:class (str "primary" (when (or (not @logo-to-upload) @image-error) " disabled"))}
           "Upload"
           submit-image]
          (when @event-logo
            [action-button/component
             {}
             (list [:i.red.remove.icon] "Remove")
             delete-image])]])
      [:div.ui.vertical.segment
       [:h2.ui.header
        "Activities"]
       [activities-table/component (:EventId event)]]
      [:div.ui.vertical.segment
       [:h2.ui.header
        "Groups"]
       [groups-table/component (:EventId event)]]
      (when can-check-in
        [:div.ui.vertical.segment
         [:h2.ui.header
          "Attendees"]
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
              [:td
               (if (:CheckinTime attendee)
                 [:button.ui.small.right.floated.button.green
                  {:style {:width "105px"}}
                  "Checked in"]
                 [action-button/component
                  {:class "small right floated"
                   :style {:width "105px"}}
                  "Check in"
                  (check-in (:AttendeeId attendee))])
               [:a.ui.right.floated.small.labeled.button
                {:href (routes/event-attendee
                         {:EventId (:EventId event)
                          :AttendeeId (:AttendeeId attendee)})}
                "Details"
                [:i.right.chevron.icon]]]])]
          [:tfoot
           [:tr
            [:th {:colSpan "4"}
             [:a.ui.right.floated.small.button
              {:href (routes/event-attendees event)}
              "View all"]]]]]])
      (when is-attendee
        [:div.ui.vertical.segment
         [:h2 "Your Schedule"]
         [schedule/component scheduled-activities
          (list "Details" [:i.right.chevron.icon])
          (fn [_ activity-id]
            (js/location.assign (routes/event-activity
                                  {:EventId (:EventId event)
                                   :ActivityId activity-id})))
          [:a.ui.small.right.floated.labeled.icon.button
           {:href (routes/event-schedule event)}
           [:i.edit.icon]
           "Edit"]]])]]))

(defn page [event-id]
  (let [logo-to-upload (atom false)
        event-logo (atom false)
        image-error (atom nil)]
    (fn [event-id]
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
                            (if (js/window.confirm
                                  (str "Are you sure you want to leave "
                                       (:Name event)
                                       "?"))
                              (api/attendees-endpoint
                                :delete
                                (d/entity @api/attendees-db attendee-id)
                                #(do
                                   (callback)
                                   (api/schedules-endpoint :read nil nil)
                                   (js/location.replace (routes/events)))
                                callback)
                              (callback))))
            can-edit
            (get-in (:EventPermissions (:permissions @state/session))
                    [event-id :EditEvent])
            can-check-in
            (get-in (:EventPermissions (:permissions @state/session))
                    [event-id :EditUser])
            is-attendee
            (not (empty? (d/q '[:find [?attendee-id ...]
                                :in $ ?event-id ?user-id
                                :where
                                [?attendee-id :EventId ?event-id]
                                [?attendee-id :UserId ?user-id]]
                              @api/attendees-db
                              (:EventId event)
                              (:UserId (:user @state/session)))))
            get-logo #(api/api-call :read
                                    (str "/events/" event-id "/logos")
                                    {}
                                    (fn [json] (reset! event-logo (:URL json)))
                                    (fn [] (reset! event-logo nil)))]
        (when (seq event)
          (when (:HasLogo event)
            (get-logo))
          (cond
            (or can-edit can-check-in)
            [details-owner event activities attendees leave-event
             logo-to-upload event-logo image-error get-logo
             can-edit can-check-in is-attendee]

            is-attendee
            [details-attendee event leave-event event-logo]

            :else
            [:div.sixteen.wide.column
             [:div.ui.sixteen.column.grid
              [:div.column {:class (str (if (:HasLogo event)
                                          "thirteen"
                                          "sixteen")
                                        " wide")}
               [:div.ui.segment
                [:div.ui.vertical.segment
                 [:h1.ui.dividing.header
                  (:Name event)]
                 [event-details/component event]
                 (when (:token @state/session)
                   [:div.extra
                    [:a.ui.right.floated.button
                     {:href (routes/event-register event)}
                     "Register"
                     [:i.right.chevron.icon]]])]
                (when (seq activities)
                  [:div.ui.vertical.segment
                   [:h2.ui.dividing.header "Activities"]
                   [:div.ui.divided.items
                    (doall
                      (for [activity activities]
                        ^{:key (:ActivityId activity)}
                        [:div.item
                         [:div.content
                          [:div.header (:Name activity)]
                          [activity-details/component activity]]]))]])]]
              [logo/component @event-logo]]]))))))

(routes/register-page routes/event-chan #'page)
