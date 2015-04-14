(ns elevent-client.pages.organizations.id.edit
  (:require [reagent.core :as r :refer [atom]]
            [validateur.validation :refer [validation-set presence-of]]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.input :as input]))

(defn organization-edit-page []
  (let [form (atom {})
        validator (validation-set (presence-of :Name))]
    (fn []
      (let [{:keys [Name]} @form
            errors (validator @form)
            create-organization
            (fn [form]
              (fn [callback]
                (api/organizations-endpoint :create
                                        (assoc form
                                          :AdminId (get-in @state/session
                                                           [:user :UserId]))
                                        #(do
                                           (callback)
                                           (js/location.replace
                                             (routes/organizations))))))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:form.ui.form
           [:div.ui.vertical.segment
            [:h2.ui.dividing.header
             "Add an Organization"]
            [:div.field
             [:div.required.field {:class (when (and Name (:Name errors))
                                            :error)}
              [:label "Organization Name"]
              [input/component :text {} (r/wrap Name swap! form assoc :Name)]]]
            [action-button/component
             {:class [:primary (when (seq errors) :disabled)]}
             "Add"
             (create-organization @form)]]]]]))))
