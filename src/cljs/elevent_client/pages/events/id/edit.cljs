(ns elevent-client.pages.events.id.edit
  (:require
    [goog.string :as string]

    [reagent.core :as r :refer [atom]]
    [datascript :as d]
    [validateur.validation :refer [format-of presence-of validation-set]]
    [cljs-time.coerce :refer [to-date]]
    [cljs-time.core :refer [hours now plus]]

    [elevent-client.api :as api]
    [elevent-client.routes :as routes]
    [elevent-client.state :as state]
    [elevent-client.components.action-button :as action-button]
    [elevent-client.components.input :as input]
    [elevent-client.components.date-selector :as date-selector]))

(def validator
  (validation-set
    (presence-of :Name)
    (presence-of :Venue)
    (presence-of :StartDate)
    (presence-of :EndDate)
    (format-of :TicketPrice
               :format      #"^\d*\.\d\d$"
               :allow-nil   true
               :allow-blank true)
    (format-of :StartDate :format #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d")
    (format-of :EndDate   :format #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d")))

(defn page [& [event-id]]
  (let [form (atom {})
        clone-id (atom 0)]
    (when event-id
      (if-let [event (seq (d/entity @api/events-db event-id))]
        (reset! form (let [event (into {} event)]
                       (assoc event
                         :TicketPrice (str (:TicketPrice event)))))
        (add-watch api/events-db
                   :event-edit
                   (fn [_ _ _ db]
                     (reset! form (let [event (into {} (d/entity db event-id))]
                                    (assoc event
                                      :TicketPrice (str (:TicketPrice event)))))
                     (remove-watch api/events-db :event-edit)))))
    (add-watch clone-id :clone
               (fn [_ _ _ id]
                 (when-not (zero? (int id))
                   (let [clone-event (->> id
                                          int
                                          (d/entity @api/events-db)
                                          seq
                                          (into {}))]
                     (reset!
                       form
                       (dissoc
                         (assoc
                           clone-event
                           :TicketPrice
                           (if (> (:TicketPrice clone-event) 0)
                             (string/format "%.2f" (:TicketPrice clone-event))
                             ""))
                         :EventId))))))
    (fn []
      (let [{:keys [Name OrganizationId Venue StartDate EndDate RequiresPayment
                    TicketPrice Description]}
            @form

            errors
            (validator @form)

            clonable-events
            (cons ["None" 0]
                  (doall
                    (filter (fn [[event-name event-id]]
                              (get-in @state/session
                                      [:permissions
                                       :EventPermissions
                                       event-id
                                       :EditEvent]))
                            (d/q '[:find ?name ?id
                                   :where [?id :Name ?name]]
                                 @api/events-db))))

            associated-organizations
            (cons ["None" 0]
                  (d/q '[:find ?name ?id
                         :where [?id :Name ?name]]
                       @api/organizations-db))

            create-event
            (fn [form]
              (fn [callback]
                (when (empty? errors)
                  (api/events-endpoint (if event-id :update :create)
                                       form
                                       #(do
                                          (callback)
                                          (js/location.replace
                                            (routes/events-explore)))))))]
        [:div.sixteen.wide.column
         [:div.ui.top.attached.tabular.menu
          [:a.item {:href (routes/events)}
           "Events"]
          [:a.item {:href (routes/events-explore)}
           "Explore"]
          [:a.item {:href (routes/events-owned)}
           "Owned"]
          [:a.active.item {:href (routes/event-add)}
           "Add"]]
         [:div.ui.bottom.attached.segment
          (prn-str @form)
          [:form.ui.form
           [:div.ui.vertical.segment
            [:h2.ui.dividing.header (if event-id "Edit" "Add") " an Event"]
            [:div.two.fields
             [:div.required.field {:class (when (and Name (:Name errors))
                                            :error)}
              [:label "Name"]
              [input/component :text {} (r/wrap Name swap! form assoc :Name)]]
             [:div.field
              [:label "Clone From"]
              [input/component :select {} clonable-events clone-id]]]
            [:div.two.fields
             [:div.required.field
              [:label "Organization"]
              [input/component :select {} associated-organizations
               (r/wrap OrganizationId swap! form assoc :OrganizationId)]]
             [:div.required.field {:class (when (and Venue (:Venue errors))
                                            :error)}
              [:div.required.field
               [:label "Venue"]
               [input/component :text {} (r/wrap Venue swap! form assoc :Venue)]]]]
            (let [start-date (r/wrap StartDate swap! form assoc :StartDate)
                  end-date   (r/wrap EndDate swap! form assoc :EndDate)]
              [:div.two.fields
               [:div.required.field {:class (when (and StartDate
                                                       (:StartDate errors))
                                              :error)}
                [:label "Start Date"]
                [date-selector/component {:date-atom start-date
                                :max-date-atom end-date
                                :pikaday-attrs {:minDate (-> (now)
                                                             (plus (hours 6))
                                                             to-date)}}]]
               [:div.required.field {:class (when (and EndDate
                                                       (:EndDate errors))
                                              :error)}
                [:label "End Date"]
                [date-selector/component {:date-atom end-date
                                ;:min-date-atom start-date
                                }]]])
            [:div.field
             [:div.four.wide.field {:class (when (and TicketPrice
                                                      (:TicketPrice errors))
                                             :error)}
              [:label "Ticket Price"]
              [:div.ui.labeled.input
               [:div.ui.label "$"]
               [input/component :text {}
                (r/wrap TicketPrice swap! form assoc :TicketPrice)]]]]
            [:div.field
             [:div.field
              ; TODO: someday make UI checkboxes work
              #_[:div.field
              [(with-meta identity
                          {:component-did-mount
                           #(.checkbox (js/$ ".ui.checkbox"))})
               [:div#requires-payment.ui.checkbox
                [:input {:type "checkbox"}]
                [:label "Ticket required"]]]]
               [:label
                [:input#requires-payment
                 {:type "checkbox"
                  :on-change #(swap! form assoc :RequiresPayment
                                     (if (nil? (:RequiresPayment @form))
                                       true
                                       (not (:RequiresPayment @form))))}]
                " Ticket required"]]]
            [:div.field
             [:label "Description"]
             [input/component :textarea {}
              (r/wrap Description swap! form assoc :Description)]]
            [action-button/component
             {:class [:primary (when (seq errors) :disabled)]}
             (if event-id "Edit" "Add")
             (create-event @form)]]]]]))))

(routes/register-page routes/event-edit-chan #'page)
