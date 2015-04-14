(ns elevent-client.pages.events.id.activities.id.edit
  (:require
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
                            year]]
    [cljs-time.format :refer [formatters unparse]]

    [elevent-client.api :as api]
    [elevent-client.routes :as routes]
    [elevent-client.components.activity-table :as activity-table]
    [elevent-client.components.action-button :as action-button]
    [elevent-client.components.input :as input]
    [elevent-client.components.date-selector :as date-selector]
    [elevent-client.components.event-details :as event-details]))

(defn page [event-id & [activity-id]]
  (let [form (atom {:EventId event-id :EnrollmentCap ""})
        reset-form! #(reset! form {:EventId event-id :EnrollmentCap ""})
        validator (validation-set (presence-of :Name)
                                  (presence-of :StartTime)
                                  (presence-of :EndTime)
                                  (format-of :EnrollmentCap :format #"^\d*$"
                                             :allow-blank true
                                             :allow-nil true)
                                  (format-of
                                    :StartTime
                                    :format #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d")
                                  (format-of
                                    :EndTime
                                    :format #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d"))]
    (when activity-id
      (if-let [activity (seq (d/entity @api/activities-db activity-id))]
        (reset! form (let [activity (into {} activity)]
                       (assoc activity
                         :EnrollmentCap (str (:EnrollmentCap activity)))))
        (add-watch api/activities-db
                   :activity-edit
                   (fn [_ _ _ _]
                     (reset! form
                             (let [activity
                                   (into {} (d/entity @api/activities-db
                                                      activity-id))]
                               (assoc activity
                                 :EnrollmentCap
                                 (str (:EnrollmentCap activity)))))
                     (remove-watch api/activities-db :activity-edit)))))
    (fn [event-id]
      (let [{:keys [Name Location EnrollmentCap StartTime EndTime Description]}
            @form

            errors
            (validator @form)

            event
            (into {} (d/entity @api/events-db event-id))

            activities
            (doall (map #(d/entity @api/activities-db %)
                        (d/q '[:find [?e ...]
                               :in $ ?event-id
                               :where
                               [?e :EventId ?event-id]]
                             @api/activities-db)))

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
                      (assoc form
                        :StartTime
                        (unparse (:date-hour-minute-second formatters)
                                 (from-string start-time))
                        :EndTime
                        (unparse (:date-hour-minute-second formatters)
                                 (from-string end-time))))
                    (fn [_]
                       (callback)
                       (when-not activity-id (reset-form!)))))))]
        (when (seq event)
          [:div.sixteen.wide.column
           [:div.ui.segment
            [:div.ui.vertical.segment
             [:h2.ui.dividing.header
              (:Name event)]
             [event-details/component event]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              (if activity-id "Edit" "Add") " activity"]
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
              (let [start-time
                    (r/wrap StartTime swap! form assoc :StartTime)
                    end-time
                    (r/wrap EndTime swap! form assoc :EndTime)
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
                                  :max-date-atom end-time
                                  :pikaday-attrs {:minDate event-start-js
                                                  :maxDate event-end-js
                                                  :defaultDate event-start
                                                  :setDefaultDate false
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
                                                  :defaultDate event-start
                                                  :setDefaultDate false
                                                  }
                                  :static-attrs  {:min-date event-start
                                                  :max-date event-end}}]]])
              [:div.field
               [:label "Description"]
               [input/component :textarea {}
                (r/wrap Description swap! form assoc :Description)]]
              [action-button/component
               {:class [:primary (when (seq errors) :disabled)]}
               (if activity-id "Edit" "Add")
               (create-activity @form)]]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              "Activities"]
             [activity-table/component event-id]]]])))))

(routes/register-page routes/event-activity-edit-chan #'page)
