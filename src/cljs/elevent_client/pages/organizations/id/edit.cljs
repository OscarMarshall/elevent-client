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
        validator (validation-set (presence-of :Name))]
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
    (fn []
      (let [{:keys [Name]} @form
            errors (validator @form)
            create-organization
            (fn [form]
              (fn [callback]
                (api/organizations-endpoint (if organization-id :update :create)
                                        (assoc form
                                          :AdminId (get-in @state/session
                                                           [:user :UserId]))
                                        #(do
                                           (callback)
                                           (js/location.replace
                                             (routes/organizations))))))]
        [:div.sixteen.wide.column
         [organizations/tabs (if organization-id :edit :add)]
         [:div.ui.bottom.attached.segment
          [:form.ui.form
           [:div.ui.vertical.segment
            [:h2.ui.dividing.header
             (if organization-id "Edit" "Add") " an Organization"]
            [:div.field
             [:div.required.field {:class (when (and Name (:Name errors))
                                            :error)}
              [:label "Organization Name"]
              [input/component :text {} (r/wrap Name swap! form assoc :Name)]]]
            [action-button/component
             {:class [:primary (when (seq errors) :disabled)]}
             (if organization-id "Edit" "Add")
             (create-organization @form)]]]]]))))

(routes/register-page routes/organization-edit-chan #'page)
