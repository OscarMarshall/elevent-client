(ns elevent-client.pages.events.id.edit
  (:require
    [goog.string :as string]

    [reagent.core :as r :refer [atom]]
    [datascript :as d]
    [validateur.validation :refer [format-of presence-of validation-set]]
    [cljs-time.coerce :refer [to-date from-string]]
    [cljs-time.core :refer [hours now plus]]

    [elevent-client.api :as api]
    [elevent-client.routes :as routes]
    [elevent-client.state :as state]
    [elevent-client.components.action-button :as action-button]
    [elevent-client.components.help-icon :as help-icon]
    [elevent-client.components.input :as input]
    [elevent-client.components.date-selector :as date-selector]
    [elevent-client.pages.events.core :as events]))

(def validator
  "Form validator"
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
  "Edit add or event page"
  ; If editing, but you don't have edit permissions, don't display page.
  (if (and event-id
           (not (get-in (:EventPermissions (:permissions @state/session))
                        [event-id :EditEvent])))
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:p "You do not have permission to edit this event."]]]
  (let [form (atom {})
        clone-id (atom 0)
        organization-id (atom 0)]
    ; If event-id exists, we are editing. Prepopulate form.
    (when event-id
      (if-let [event (seq (d/entity @api/events-db event-id))]
        (let [event (into {} event)]
          (reset! form (assoc event
                         :TicketPrice (if (> (:TicketPrice event) 0)
                                        (string/format "%.2f" (:TicketPrice event))
                                        "")))
          (reset! organization-id (:OrganizationId event)))
        (add-watch api/events-db
                   :event-edit
                   (fn [_ _ _ db]
                     (let [event (into {} (d/entity db event-id))]
                       (reset! form
                               (assoc event
                                 :TicketPrice (if (> (:TicketPrice event) 0)
                                                (string/format "%.2f" (:TicketPrice event))
                                                "")))
                       (reset! organization-id (:OrganizationId event)))
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
                         :EventId))
                     (reset! organization-id (:OrganizationId clone-event))))))
    (add-watch organization-id :clear-ticket-price
               (fn [_ _ _ _]
                 (when (> @organization-id 0)
                   (let [org (d/entity @api/organizations-db @organization-id)]
                     (when (nil? (:PaymentRecipientId org))
                       (swap! form dissoc :TicketPrice))))))
    (fn []
      (let [{:keys [Name OrganizationId Venue StartDate EndDate
                    TicketPrice Description]}
            @form

            errors
            (validator @form)

            ; Clonable events are events the user may edit
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

            ; Organizations the user has permission to add events to
            associated-organizations
            (some->> (-> @state/session
                         (get-in [:user :UserId]))
                     (d/entity @api/permissions-db)
                     (into {})
                     :OrganizationPermissions
                     (filter :AddEvent)
                     (map #(let [organization-id (:OrganizationId %)]
                             [(:Name (d/entity @api/organizations-db
                                               organization-id))
                              organization-id]))
                     doall
                     (cons ["None" 0]))

            create-event
            (fn [form]
              (fn [callback]
                (when (empty? errors)
                  (api/events-endpoint
                    (if event-id :update :create)
                    (assoc form :OrganizationId @organization-id)
                    ; if creating a new event, read permissions after creating
                    (fn [json]
                      (if event-id
                        (do
                          (callback)
                          (js/location.replace
                            (routes/event {:EventId event-id})))
                        (let [new-event-id (get json "EventId")]
                          (api/permissions-endpoint
                            :read
                            nil
                            #(do
                               (callback)
                               (js/location.replace
                                 (routes/event {:EventId new-event-id})))))))
                    callback))))]
        [:div.sixteen.wide.column
         [events/tabs (if event-id :edit :add)]
         [:div.ui.bottom.attached.segment
          [:form.ui.form
           [:div.ui.vertical.segment
            [:h2.ui.dividing.header (if event-id "Edit" "Add") " an Event"
             (when event-id
              [:a.ui.right.floated.small.button
               {:href (routes/event {:EventId event-id})}
               "Details"
               [:i.right.chevron.icon]])]
            (let [name-field [:div.required.field
                              {:class (when (and Name (:Name errors)) :error)}
                              [:label "Name"]
                              [input/component
                               :text
                               {}
                               (r/wrap Name swap! form assoc :Name)]]]
              (if event-id
                name-field
                [:div.two.fields
                 name-field
                 [:div.field
                  [:label "Clone From"]
                  [input/component :select {} clonable-events clone-id]]]))
            [:div.two.fields
             [:div.required.field
              [:label "Organization"]
              [input/component :select {} associated-organizations
               organization-id]]
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
                [date-selector/component
                 {:date-atom start-date
                  :pikaday-attrs (merge
                                   {:minDate (-> (now)
                                                 (plus (hours 6))
                                                 to-date)}
                                   (when @start-date
                                     {:defaultDate (-> (from-string @start-date)
                                                       (plus (hours 6))
                                                       to-date)
                                      :setDefaultDate true}))}]]
               [:div.required.field {:class (when (and EndDate
                                                       (:EndDate errors))
                                              :error)}
                [:label "End Date"]
                [date-selector/component
                 {:date-atom end-date
                  :min-date-atom start-date
                  :pikaday-attrs (merge
                                   {:minDate (-> (now)
                                                 (plus (hours 6))
                                                 to-date)}
                                   (when @end-date
                                     {:defaultDate (-> (from-string @end-date)
                                                       (plus (hours 6))
                                                       to-date)
                                      :setDefaultDate true}))}]]])
            [:div.field
             [:div.four.wide.field {:class (when (and TicketPrice
                                                      (:TicketPrice errors))
                                             :error)}
              [:label "Ticket Price "
               [help-icon/component (str "To charge for this event, please associate it with "
                                         "an organization that has set up a Stripe payments "
                                         "account.")]]
              [:div.ui.labeled.input
               [:div.ui.label "$"]
               (let [disabled? (or (not (> @organization-id 0))
                                   (let [org (d/entity @api/organizations-db
                                                       @organization-id)]
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
             (if event-id "Save" "Add")
             (create-event @form)]]]]])))))

(routes/register-page routes/event-edit-chan #'page true)
