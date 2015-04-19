(ns elevent-client.pages.organizations.id.edit
  (:require [reagent.core :as r :refer [atom]]
            [datascript :as d]
            [validateur.validation :refer [validation-set presence-of]]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.input :as input]
            [elevent-client.pages.organizations.core :as organizations]))

(defn page [& [organization-id]]
  (let [form (atom {})
        validator (validation-set (presence-of :Name))
        permissions-user (atom nil)
        permissions-event (atom nil)
        event-edit-permissions (atom nil)
        check-in-permissions (atom nil)
        org-permissions (atom nil)
        get-current-event-permissions
        (fn []
          (let [permissions (ffirst (d/q '[:find ?event-permissions
                                          :in $ ?user-id
                                          :where
                                          [?a :UserId ?user-id]
                                          [?a :EventPermissions ?event-permissions]]
                                        @api/permissions-db
                                        @permissions-user))]
            (prn permissions)
            ; Filter event permissions to match event id
            (first (filter
                     (fn [event-permissions]
                       (= (:EventId event-permissions) @permissions-event))
                     permissions))))
        get-current-org-permissions
        (fn []
          (let [permissions (ffirst (d/q '[:find ?org-permissions
                                          :in $ ?user-id
                                          :where
                                          [?a :UserId ?user-id]
                                          [?a :OrganizationPermissions ?org-permissions]]
                                        @api/permissions-db
                                        @permissions-user))]
            (prn permissions)
            ; Filter organization permissions to match org id
            (first (filter
                     (fn [org-permissions]
                       (= (:OrganizationId org-permissions) organization-id))
                     permissions))))
        sync-permissions
        (fn [_ _ _ _]
          (when (and (= @permissions-user 0)
                     (> @permissions-event 0))
            (reset! permissions-event 0))
          (let [current-event-permissions (get-current-event-permissions)
                current-org-permissions   (get-current-org-permissions)]
            (reset! event-edit-permissions (:EditEvent current-event-permissions))
            (reset! check-in-permissions (:EditUser current-event-permissions))
            (reset! org-permissions (:EditEvent current-org-permissions))))]
    (when organization-id
      (if-let [organization (seq (d/entity @api/organizations-db
                                           organization-id))]
        (reset! form (into {} organization))
        (add-watch api/organizations-db
                   :organization-edit
                   (fn [_ _ _ db]
                     (reset! form (into {} (d/entity @api/organizations-db
                                                     organization-id)))
                     (remove-watch api/organizations-db :organization-edit)))))
    (add-watch permissions-user
               :preset-permissions
               sync-permissions)
    (add-watch permissions-event
               :preset-permissions
               sync-permissions)
    (fn []
      (let [{:keys [Name]} @form
            errors (validator @form)
            create-organization
            (fn [form]
              (fn [callback]
                (api/organizations-endpoint (if organization-id :update :create)
                                            (if organization-id
                                              form
                                              (assoc form
                                                :AdminId (get-in @state/session
                                                                 [:user :UserId])))
                                            #(do
                                               (callback)
                                               (js/location.replace
                                                 (routes/organizations))))))
            organization-members
            (when organization-id
              (cons ["None" 0]
                    (doall
                      (map
                        (fn [[user-id first-name last-name email]]
                          [(str first-name " " last-name " (" email ")")
                           user-id])
                        (d/q '[:find ?user-id ?first-name ?last-name ?email
                               :in $members $users ?organization-id
                               :where
                               [$members ?a :OrganizationId ?organization-id]
                               [$members ?a :UserId         ?user-id]
                               [$users   ?b :UserId         ?user-id]
                               [$users   ?b :FirstName      ?first-name]
                               [$users   ?b :LastName       ?last-name]
                               [$users   ?b :Email          ?email]]
                             @api/memberships-db
                             @api/users-db
                             organization-id)))))
            organization-events
            (when organization-id
              (cons ["None" 0]
                    (doall
                      (map
                        (fn [[event-id event-name]]
                          [event-name event-id])
                        (d/q '[:find ?event-id ?event-name
                               :in $ ?organization-id
                               :where
                               [?a :OrganizationId ?organization-id]
                               [?a :EventId        ?event-id]
                               [?a :Name           ?event-name]]
                             @api/events-db
                             organization-id)))))
            save-permissions
            (fn [callback]
              (let [event-permissions-to-set
                    {:UserId          @permissions-user
                     :EventId         @permissions-event
                     :AddEvent        @event-edit-permissions
                     :ReadEvent       @event-edit-permissions
                     :EditEvent       @event-edit-permissions
                     :DeleteEvent     @event-edit-permissions
                     :AddActivity     @event-edit-permissions
                     :ReadActivity    @event-edit-permissions
                     :EditActivity    @event-edit-permissions
                     :DeleteActivity  @event-edit-permissions
                     :AddUser         @check-in-permissions
                     :ReadUser        @check-in-permissions
                     :EditUser        @check-in-permissions
                     :DeleteUser      @check-in-permissions
                     :SendEmail       @event-edit-permissions
                     :GrantPermission @event-edit-permissions}
                    org-permissions-to-set
                    {:UserId             @permissions-user
                     :OrganizationId     organization-id
                     :AddOrganization    @org-permissions
                     :ReadOrganization   @org-permissions
                     :EditOrganization   @org-permissions
                     :DeleteOrganization @org-permissions
                     :AddEvent           @org-permissions
                     :ReadEvent          @org-permissions
                     :EditEvent          @org-permissions
                     :DeleteEvent        @org-permissions
                     :AddActivity        @org-permissions
                     :ReadActivity       @org-permissions
                     :EditActivity       @org-permissions
                     :DeleteActivity     @org-permissions
                     :AddUser            @org-permissions
                     :ReadUser           @org-permissions
                     :EditUser           @org-permissions
                     :DeleteUser         @org-permissions
                     :SendEmail          @org-permissions
                     :GrantPermission    @org-permissions}]
                (let [current-event-permissions (get-current-event-permissions)
                      current-org-permissions   (get-current-org-permissions)]
                  ; Create/update event permissions
                  (when (> @permissions-event 0)
                    (if (nil? current-event-permissions)
                      (api/api-call :create
                                    "/eventpermission"
                                    event-permissions-to-set
                                    #(api/permissions-endpoint :read nil callback))
                      (api/api-call :update
                                    "/eventpermissions"
                                    (assoc event-permissions-to-set
                                      :EventPermissionId
                                      (:EventPermissionId current-event-permissions))
                                    #(api/permissions-endpoint :read nil callback))))
                  ; Create/update org permissions
                  (if (nil? current-org-permissions)
                    (api/api-call :create
                                  "/organizationpermission"
                                  org-permissions-to-set
                                  #(api/permissions-endpoint :read nil callback))
                    (api/api-call :update
                                  "/organizationpermissions"
                                  (assoc org-permissions-to-set
                                    :OrganizationPermissionId
                                    (:OrganizationPermissionId current-org-permissions))
                                  #(api/permissions-endpoint :read nil callback))))))]
>>>>>>> Stashed changes
        [:div.sixteen.wide.column
         [organizations/tabs (if organization-id :edit :add)]
         [:div.ui.bottom.attached.segment
          [:div.ui.vertical.segment
           [:h2.ui.dividing.header
            (if organization-id "Edit" "Add") " an Organization"]
           [:form.ui.form
            [:div.field
             [:div.required.field {:class (when (and Name (:Name errors))
                                            :error)}
              [:label "Organization Name"]
              [input/component :text {} (r/wrap Name swap! form assoc :Name)]]]
            [action-button/component
             {:class [:primary (when (seq errors) :disabled)]}
             (if organization-id "Edit" "Add")
             (create-organization @form)]]]
          (when organization-id
            [:div.ui.vertical.segment
             [:h2.ui.dividing.header
              "Edit Member Permissions"]
             [:form.ui.form
              [:div.two.fields
               [:div.field
                [:label "Choose user"]
                [input/component :select {} organization-members permissions-user]]
               (if (> @permissions-user 0)
                 [:div.field
                  [:label "Organization Permissions"]
                  [input/component :checkbox {:label "Add/edit/delete events"} org-permissions]]
                 [:div.field])]
              [:div.fields
               (when (> @permissions-user 0)
                 [:div.eight.wide.field
                  [:label "Choose event"]
                  [input/component :select {} organization-events permissions-event]])
               (if (and (> @permissions-event 0)
                        (> @permissions-user 0))
                 [:div.eight.wide.field
                  [:div.fields
                   [:div.eight.wide.field
                    [:label "Event Permissions"]
                    [input/component :checkbox {:label "Edit/delete"} event-edit-permissions]]
                   [:div.eight.wide.field
                    [:label "Attendee Permissions"]
                    [input/component :checkbox {:label "Check in"} check-in-permissions]]]]
                 [:div.eight.wide.field])]
              [action-button/component
               {:class "primary"}
               "Save"
               save-permissions]]])]]))))

(routes/register-page routes/organization-edit-chan #'page)
