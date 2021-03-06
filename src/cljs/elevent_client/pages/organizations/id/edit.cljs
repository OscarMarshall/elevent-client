;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.organizations.id.edit
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :as async :refer [put!]]
            [datascript :as d]
            [validateur.validation :refer [validation-set presence-of]]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.help-icon :as help-icon]
            [elevent-client.components.input :as input]
            [elevent-client.pages.organizations.core :as organizations]))

(defn page [& [organization-id]]
  "Organization add or edit page"
  ; If editing, but you don't have edit permissions, don't display page.
  (if (and organization-id
           (not (get-in (:OrganizationPermissions (:permissions @state/session))
                        [organization-id :EditOrganization])))
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:p "You do not have permission to edit this organization."]]]
  (let [form (atom {})
        validator (validation-set (presence-of :Name))

        ; State atoms for the permissions assignment form
        ; Current user
        permissions-user (atom nil)
        ; Current event
        permissions-event (atom nil)
        ; Edit event permissions for the user/event
        event-edit-permissions (atom nil)
        ; Event check-in permissions for the user/event
        check-in-permissions (atom nil)
        ; Organization edit permissions for the user
        org-permissions (atom nil)
        ; Organization event add/edit permissions for the user/event
        org-event-permissions (atom nil)

        ; Get the current event-level permissions of the user/event
        get-current-event-permissions
        (fn []
          (let [permissions (ffirst (d/q '[:find ?event-permissions
                                           :in $ ?user-id
                                           :where
                                           [?a :UserId ?user-id]
                                           [?a :EventPermissions ?event-permissions]]
                                         @api/permissions-db
                                         @permissions-user))]
            ; Filter event permissions to match event id
            (first (filter
                     (fn [event-permissions]
                       (= (:EventId event-permissions) @permissions-event))
                     permissions))))

        ; Get the current org-level permissions of the user
        get-current-org-permissions
        (fn []
          (let [permissions (ffirst (d/q '[:find ?org-permissions
                                           :in $ ?user-id
                                           :where
                                           [?a :UserId ?user-id]
                                           [?a :OrganizationPermissions ?org-permissions]]
                                         @api/permissions-db
                                         @permissions-user))]
            ; Filter organization permissions to match org id
            (first (filter
                     (fn [org-permissions]
                       (= (:OrganizationId org-permissions) organization-id))
                     permissions))))

        ; Update the form checkboxes with the current event permissions
        sync-event-permissions
        (fn []
          (when (and (= @permissions-user 0)
                     (> @permissions-event 0))
            (reset! permissions-event 0))
          (let [current-event-permissions (get-current-event-permissions)]
            (reset! event-edit-permissions (:EditEvent current-event-permissions))
            (reset! check-in-permissions (:EditUser current-event-permissions))))

        ; Update the form checkboxes with the current org permissions
        sync-org-permissions
        (fn []
          (when (and (= @permissions-user 0)
                     (> @permissions-event 0))
            (reset! permissions-event 0))
          (let [current-org-permissions   (get-current-org-permissions)]
            (reset! org-permissions (:EditOrganization current-org-permissions))
            (reset! org-event-permissions (:EditEvent current-org-permissions))))]
    ; If organization-id is defined, we are editing. Prefill the form.
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
    ; Watch for updates of the permissions fields
    (add-watch permissions-user
               :preset-org-permissions
               (fn [_ _ _ _]
                 (sync-event-permissions)
                 (sync-org-permissions)))
    (add-watch permissions-event
               :preset-event-permissions
               (fn [_ _ _ _]
                 (sync-event-permissions)))
    (fn []
      (let [{:keys [Name PaymentRecipientId]} @form
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
                                            (fn []
                                              (if organization-id
                                                (do
                                                  (callback)
                                                  (js/location.replace
                                                    (routes/organizations-owned)))
                                                ; if creating a new organization, update permissions and memberships
                                                (api/memberships-endpoint
                                                  :read
                                                  nil
                                                  (fn []
                                                    (api/permissions-endpoint
                                                      :read
                                                      nil
                                                      #(do
                                                         (callback)
                                                         (js/location.replace
                                                           (routes/organizations-owned))))))))
                                            callback)))
            delete-member
            (fn [member-id first-name last-name]
              (when (js/window.confirm (str "Are you sure you want to remove "
                                          (str first-name " " last-name)
                                          " from this organization?"))
                (api/memberships-endpoint :delete {:MembershipId member-id} nil)))
            organization-members
            (when organization-id
              (d/q '[:find ?a ?user-id ?first-name ?last-name ?email
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
                   organization-id))
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
              ; Define permissions structures expected by API
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
                     :AddUser         (or @event-edit-permissions @check-in-permissions)
                     :ReadUser        (or @event-edit-permissions @check-in-permissions)
                     :EditUser        (or @event-edit-permissions @check-in-permissions)
                     :DeleteUser      (or @event-edit-permissions @check-in-permissions)
                     :SendEmail       @event-edit-permissions
                     :GrantPermission @event-edit-permissions}
                    org-permissions-to-set
                    {:UserId             @permissions-user
                     :OrganizationId     organization-id
                     :AddOrganization    @org-permissions
                     :ReadOrganization   @org-permissions
                     :EditOrganization   @org-permissions
                     :DeleteOrganization @org-permissions
                     :AddEvent           @org-event-permissions
                     :ReadEvent          @org-event-permissions
                     :EditEvent          @org-event-permissions
                     :DeleteEvent        @org-event-permissions
                     :AddActivity        @org-event-permissions
                     :ReadActivity       @org-event-permissions
                     :EditActivity       @org-event-permissions
                     :DeleteActivity     @org-event-permissions
                     :AddUser            @org-permissions
                     :ReadUser           @org-permissions
                     :EditUser           @org-permissions
                     :DeleteUser         @org-permissions
                     :SendEmail          @org-permissions
                     :GrantPermission    @org-permissions}]
                (let [current-event-permissions (get-current-event-permissions)
                      current-org-permissions   (get-current-org-permissions)
                      error-callback
                      (fn [callback]
                        (fn [error]
                          (callback)
                          (if (= (:status error) 403)
                            (put! state/add-messages-chan
                                  [:forbidden-action
                                   [:negative "You do not have permission to perform that action"]])
                            (put! state/add-messages-chan
                                  [:server-error
                                   [:negative "An error occurred."]]))))]
                  ; Create/update event permissions
                  (when (> @permissions-event 0)
                    ; Determine whether creating or updating
                    (if (nil? current-event-permissions)
                      (api/api-call :create
                                    "/eventpermission"
                                    event-permissions-to-set
                                    #(api/permissions-endpoint :read nil callback)
                                    (error-callback callback))
                      (api/api-call :update
                                    "/eventpermissions"
                                    (assoc event-permissions-to-set
                                      :EventPermissionId
                                      (:EventPermissionId current-event-permissions))
                                    #(api/permissions-endpoint :read nil callback)
                                    (error-callback callback))))
                  ; Create/update org permissions
                  (if (nil? current-org-permissions)
                    ; Determine whether creating or updating
                    (api/api-call :create
                                  "/organizationpermission"
                                  org-permissions-to-set
                                  #(api/permissions-endpoint :read nil callback)
                                  (error-callback callback))
                    (api/api-call :update
                                  "/organizationpermissions"
                                  (assoc org-permissions-to-set
                                    :OrganizationPermissionId
                                    (:OrganizationPermissionId current-org-permissions))
                                  #(api/permissions-endpoint :read nil callback)
                                  (error-callback callback))))))]
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
            [:div.one.field
             [:div.eight.wide.field
              [:label "Stripe Recipient ID "
               [help-icon/component (str "If your organization would like to charge for events, "
                                         "you must create a Stripe account and input your Recipient "
                                         "ID here.")]]
              [input/component :text {} (r/wrap PaymentRecipientId swap! form assoc :PaymentRecipientId)]]]
            [action-button/component
             {:class (str "primary" (when (seq errors) " disabled"))
              :type  :submit}
             (if organization-id "Save" "Add")
             (create-organization @form)]]]
          (when organization-id
            [:div.ui.vertical.segment
             [:div.ui.dividing.header
              "Edit Members"]
             [:table.ui.table
              [:thead
               [:th "Name"]
               [:th "Email"]
               [:th "Actions"]]
              [:tbody
               (for [[member-id user-id first-name last-name email] organization-members]
                 ^{:key user-id}
                 [:tr
                  [:td (str first-name " " last-name)]
                  [:td email]
                  [:td [:i.red.remove.icon.link
                        {:on-click #(delete-member member-id first-name last-name)}]]])]]])
          (when organization-id
            [:div.ui.vertical.segment
             [:h2.ui.dividing.header
              "Edit Member Permissions"]
             [:form.ui.form
              [:div.two.fields
               [:div.field
                [:label "Choose user"]
                [input/component :select {}
                 (cons ["None" 0]
                       (doall
                         (map
                           (fn [[member-id user-id first-name last-name email]]
                             [(str first-name " " last-name " (" email ")")
                              user-id])
                           organization-members)))
                 permissions-user]]
               (if (> @permissions-user 0)
                 [:div.field
                  [:div.fields
                   [:div.eight.wide.field
                    [:label "Organization Permissions"]
                    [input/component :checkbox {:label "Edit/delete organization"} org-permissions]]
                   [:div.eight.wide.field
                    [:label "Organization Event Permissions"]
                    [input/component :checkbox {:label "Add/edit/delete events"} org-event-permissions]]]]
                 [:div.field])]
              ; Dynamically show fields
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
               save-permissions]]])]])))))

(routes/register-page routes/organization-edit-chan #'page)
