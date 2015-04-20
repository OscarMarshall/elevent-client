(ns elevent-client.pages.organizations.owned
  (:require [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.pages.organizations.core :as organizations]))

(defn page []
  (let [owned-organizations
        (doall (map #(into {} (d/entity @api/organizations-db %))
                    (keys
                      (into {}
                            (filter
                              (fn [[org-id org-permissions]]
                                (:EditOrganization org-permissions))
                              (:OrganizationPermissions (:permissions @state/session)))))))]
    [:div.sixteen.wide.column
     [organizations/tabs :owned]
     [:div.ui.bottom.attached.segment
      [:div.ui.vertical.segment
       [:h1.ui.header "Organizations You Own"]]
      [:div.ui.vertical.segment
       [:div.ui.divided.items
        (for [organization owned-organizations]
          ^{:key (:OrganizationId organization)}
          (when (:OrganizationId organization)
            [:div.item
             [:div.content
              [:a.header
               (:Name organization)]
              [:div.extra
               ; TODO: make this work
               [:a.ui.right.floated.small.button
                {:href (routes/events-explore
                         {:query-params (select-keys organization
                                                     [:OrganizationId])})}
                "View events"
                [:i.right.chevron.icon]]
               [:a.ui.right.floated.small.button
                {:href (routes/organization-edit organization)}
                "Edit"
                [:i.right.chevron.icon]]
               [action-button/component
                {:class "ui right floated small negative"}
                "Delete"
                (fn [callback]
                  (api/organizations-endpoint
                    :delete
                    organization
                    ; Update permissions
                    #(api/permissions-endpoint
                       :read
                       nil
                       callback)))]]]]))]]]]))

(routes/register-page routes/organizations-owned-chan #'page true)
