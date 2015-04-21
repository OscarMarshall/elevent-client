(ns elevent-client.pages.events.id.groups.id.edit
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
                            year
                            plus
                            hours]]
    [cljs-time.format :refer [formatters unparse]]

    [elevent-client.api :as api]
    [elevent-client.routes :as routes]
    [elevent-client.components.groups-table :as groups-table]
    [elevent-client.components.action-button :as action-button]
    [elevent-client.components.input :as input]
    [elevent-client.components.date-selector :as date-selector]
    [elevent-client.components.event-details :as event-details]))

(defn page [event-id & [group-id]]
  (let [form (atom {:EventId event-id :EnrollmentCap ""})
        reset-form! #(reset! form {:EventId event-id :EnrollmentCap ""})
        validator (validation-set (presence-of :Name))]
    (when group-id
      (if-let [group (seq (d/entity @api/groups-db group-id))]
        (reset! form (into {} group))
        (add-watch api/groups-db
                   :group-edit
                   (fn [_ _ _ db]
                     (reset! form (into {} (d/entity db group-id)))
                     (remove-watch api/groups-db :group-edit)))))
    (fn [event-id]
      (let [{:keys [Name Location EnrollmentCap StartTime EndTime Description]}
            @form

            errors
            (validator @form)

            event
            (into {} (d/entity @api/events-db event-id))

            groups
            (doall (map #(d/entity @api/groups-db %)
                        (d/q '[:find [?e ...]
                               :in $ ?event-id
                               :where
                               [?e :EventId ?event-id]]
                             @api/groups-db)))

            create-group
            (fn [form]
              (fn [callback]
                (when (empty? errors)
                  (api/groups-endpoint
                    (if group-id
                      :update
                      :create)
                    form
                    (fn [_]
                       (callback)
                       (when-not group-id (reset-form!)))
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
              (if group-id "Edit" "Add") " a Group"]
             [:form.ui.form {:on-submit
                             (fn [e]
                               (when (empty? errors)
                                 (.preventDefault e)
                                 (api/groups-endpoint (if group-id
                                                        :update
                                                        :create)
                                                      @form
                                                      #(reset-form!))))}
              [:div.required.field {:class (when (and Name (:Name errors))
                                             :error)}
               [:label "Name"]
               [input/component
                :text
                {}
                (r/wrap Name swap! form assoc :Name)]]
              [action-button/component
               {:class (str "primary" (when (seq errors) " disabled"))}
               (if group-id "Edit" "Add")
               (create-group @form)]]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              "Groups"]
             [groups-table/component event-id]]]])))))

(routes/register-page routes/event-group-edit-chan #'page true)
