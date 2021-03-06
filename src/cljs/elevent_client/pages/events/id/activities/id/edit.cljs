;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.events.id.activities.id.edit
  (:require
    [goog.string :as string]
    [reagent.core :as r :refer [atom]]
    [validateur.validation :refer [format-of presence-of validation-set]]
    [datascript :as d]
    [cljs-time.coerce :refer [from-string
                              to-date]]
    [cljs-time.core :refer [date-midnight
                            day
                            hour
                            local-date
                            local-date-time
                            minute
                            month
                            year
                            plus
                            hours]]
    [cljs-time.format :refer [formatters unparse]]

    [elevent-client.api :as api]
    [elevent-client.routes :as routes]
    [elevent-client.state :as state]
    [elevent-client.components.activities-table :as activities-table]
    [elevent-client.components.action-button :as action-button]
    [elevent-client.components.help-icon :as help-icon]
    [elevent-client.components.input :as input]
    [elevent-client.components.date-selector :as date-selector]
    [elevent-client.components.event-details :as event-details]))

(defn page [event-id & [activity-id]]
  "Activity add/edit page"
  ; If editing, but you don't have edit permissions, don't display page.
  (if (and event-id
           (not (get-in (:EventPermissions (:permissions @state/session))
                        [event-id :EditEvent])))
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:p "You do not have permission to edit activities for this event."]]]
  (let [form (atom {:EventId event-id :EnrollmentCap ""})
        reset-form! #(reset! form {:EventId event-id :EnrollmentCap ""})
        validator (validation-set (presence-of :Name)
                                  (presence-of :StartTime)
                                  (presence-of :EndTime)
                                  (format-of :EnrollmentCap :format #"^\d*$"
                                             :allow-blank true
                                             :allow-nil true)
                                  (format-of :TicketPrice
                                             :format      #"^\d*\.\d\d$"
                                             :allow-nil   true
                                             :allow-blank true)
                                  (format-of
                                    :StartTime
                                    :format #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d")
                                  (format-of
                                    :EndTime
                                    :format #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d"))]
    ; If activity-id is defined (editing), prepopulate form
    (when activity-id
      (if-let [activity (seq (d/entity @api/activities-db activity-id))]
        (reset! form (let [activity (into {} activity)]
                       (assoc activity
                         ; Make sure number fields don't prepopulate with 0
                         :EnrollmentCap (if (> (:EnrollmentCap activity) 0)
                                          (str (:EnrollmentCap activity))
                                          "")
                         :TicketPrice   (if (> (:TicketPrice activity) 0)
                                          (string/format "%.2f" (:TicketPrice activity))
                                          ""))))
        ; Add watch to activities db so changes are reflected in form
        (add-watch api/activities-db
                   :activity-edit
                   (fn [_ _ _ _]
                     (reset! form
                             (let [activity
                                   (into {} (d/entity @api/activities-db
                                                      activity-id))]
                               (assoc activity
                                 :EnrollmentCap
                                 (if (> (:EnrollmentCap activity) 0)
                                   (str (:EnrollmentCap activity))
                                   "")
                                 :TicketPrice
                                 (if (> (:TicketPrice activity) 0)
                                   (string/format "%.2f" (:TicketPrice activity))
                                   ""))))
                     (remove-watch api/activities-db :activity-edit)))))
    (fn [event-id]
      (let [{:keys [Name Location EnrollmentCap StartTime EndTime TicketPrice Description]}
            @form

            event
            (into {} (d/entity @api/events-db event-id))

            StartTime
            (or (:StartTime @form)
                (:StartDate event))

            EndTime
            (or (:EndTime @form)
                (:StartDate event))

            errors
            (do
              ; Add start/end times to form state
              (swap! form assoc
                     :StartTime StartTime
                     :EndTime   EndTime)
             (validator @form))

            ; All event activities
            activities
            (doall (map #(d/entity @api/activities-db %)
                        (d/q '[:find [?e ...]
                               :in $ ?event-id
                               :where
                               [?e :EventId ?event-id]]
                             @api/activities-db)))

            ; Create or update activity
            create-activity
            (fn [form]
              (fn [callback]
                (when (empty? errors)
                  (api/activities-endpoint
                    (if activity-id
                      :update
                      :create)
                    (let [start-time (:StartTime form)
                          end-time   (:EndTime form)]
                      ; Send date fields in proper format
                      (assoc form
                        :StartTime
                        (unparse (:date-hour-minute-second formatters)
                                 (from-string start-time))
                        :EndTime
                        (unparse (:date-hour-minute-second formatters)
                                 (from-string end-time))))
                    (fn [_]
                       (callback)
                       (when-not activity-id (reset-form!))
                       (js/location.replace (routes/event-activity-add event)))
                    callback))))]
        (when (seq event)
          [:div.sixteen.wide.column
           [:div.ui.segment
            [:div.ui.vertical.segment
             [:h2.ui.dividing.header
              (:Name event)]
             [event-details/component event]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              (if activity-id "Edit" "Add") " an Activity"]
             [:form.ui.form {:on-submit
                             (fn [e]
                               (when (empty? errors)
                                 (.preventDefault e)
                                 (api/activities-endpoint (if activity-id
                                                        :update
                                                        :create)
                                                      @form
                                                      #(reset-form!))))}
              [:div.one.field
               [:div.required.field {:class (when (and Name (:Name errors))
                                              :error)}
                [:label "Name"]
                [input/component :text {} (r/wrap Name
                                                  swap! form assoc :Name)]]]
              [:div.two.fields
               [:div.field
                [:label "Location"]
                [input/component
                 :text
                 {}
                 (r/wrap Location swap! form assoc :Location)]]
               [:div.field {:class (when (and EnrollmentCap
                                              (:EnrollmentCap errors))
                                     :error)}
                [:label "Enrollment Cap"]
                [input/component
                 :text
                 {}
                 (r/wrap EnrollmentCap swap! form assoc :EnrollmentCap)]]]
              ; Initialize fields for datepicker
              (let [; Atoms for input fields
                    start-time
                    (r/wrap StartTime swap! form assoc :StartTime)
                    end-time
                    (r/wrap EndTime swap! form assoc :EndTime)

                    ; Lower and upper bounds (event start/end dates)
                    event-start
                    (let [d (from-string (:StartDate event))]
                      (local-date-time (year d)
                                       (month d)
                                       (day d)
                                       (hour d)
                                       (minute d)))
                    event-end
                    (let [d (from-string (:EndDate event))]
                      (local-date-time (year d)
                                       (month d)
                                       (day d)
                                       (hour d)
                                       (minute d)))

                    ; Need a JavaScript date object for datepicker
                    event-start-js
                    (let [d (from-string (:StartDate event))]
                      (to-date (date-midnight (local-date (year d)
                                                          (month d)
                                                          (day d)))))
                    event-end-js
                    (let [d (from-string (:EndDate event))]
                      (to-date (date-midnight (local-date-time (year d)
                                                               (month d)
                                                               (day d)
                                                               23
                                                               59))))]
                [:div.two.fields
                 [:div.required.field {:class (when (and StartTime
                                                         (:StartTime errors))
                                                "error")}
                  [:label "Start Time"]
                  [date-selector/component {:date-atom start-time
                                  :pikaday-attrs {:minDate event-start-js
                                                  :maxDate event-end-js
                                                  :defaultDate (-> (if @start-time
                                                                     (from-string @start-time)
                                                                     event-start)
                                                                   (plus (hours 6))
                                                                   to-date)
                                                  :setDefaultDate true
                                                  }
                                  :static-attrs  {:min-date event-start
                                                  :max-date event-end}}]]
                 [:div.required.field {:class (when (and EndTime
                                                         (:EndTime errors))
                                                "error")}
                  [:label "End Time"]
                  [date-selector/component {:date-atom end-time
                                  :min-date-atom start-time
                                  :pikaday-attrs {:minDate event-start-js
                                                  :maxDate event-end-js
                                                  :defaultDate (-> (if @end-time
                                                                     (from-string @end-time)
                                                                     event-start)
                                                                   (plus (hours 6))
                                                                   to-date)
                                                  :setDefaultDate true
                                                  }
                                  :static-attrs  {:min-date event-start
                                                  :max-date event-end}}]]])
              [:div.field
               [:div.four.wide.field {:class (when (and TicketPrice
                                                        (:TicketPrice errors))
                                               :error)}
                [:label "Ticket Price "
                 [help-icon/component (str "To charge for this activity, please associate its event with "
                                           "an organization that has set up a Stripe payments "
                                           "account.")]]
                [:div.ui.labeled.input
                 [:div.ui.label "$"]
                 (let [disabled? (or (not (> (:OrganizationId event) 0))
                                     (let [org (d/entity @api/organizations-db
                                                         (:OrganizationId event))]
                                       (nil? (:PaymentRecipientId org))))]
                   [input/component :text {:disabled disabled?}
                    (r/wrap TicketPrice swap! form assoc :TicketPrice)])]
                (when (and TicketPrice (:TicketPrice errors))
                  [:div.ui.red.pointing.prompt.label
                   "Please enter a dollar amount"])]]
              [:div.field
               [:label "Description"]
               [input/component :textarea {}
                (r/wrap Description swap! form assoc :Description)]]
              [action-button/component
               {:class (str "primary" (when (seq errors) " disabled"))}
               (if activity-id "Save" "Add")
               (create-activity @form)]
              ; If editing, show cancel button
              (when activity-id
                [:div.ui.button
                 {:on-click #(js/location.replace (routes/event-activity-add event))}
                 "Cancel"])]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              "Activities"]
             [activities-table/component event-id]]]]))))))

(routes/register-page routes/event-activity-edit-chan #'page true)
