(ns elevent-client.pages.organizations.core
  (:require [datascript :as d]
            [elevent-client.routes :as routes]
            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]))

(defn page []
  (let [organizations-joined
        (->> (d/q '[:find ?organization-id ?membership-id
                    :in $ ?user-id
                    :where
                    [?membership-id :UserId ?user-id]
                    [?membership-id :OrganizationId ?organization-id]]
                  @api/memberships-db
                  (get-in @state/session [:user :UserId]))
             (map (fn [[organization-id membership-id]]
                    (assoc (into {} (d/entity @api/organizations-db
                                              organization-id))
                      :MembershipId membership-id)))
             doall)]
    [:div.sixteen.wide.column
     [:div.ui.top.attached.tabular.menu
      [:a.active.item {:href (routes/organizations)}
       "Organizations"]
      [:a.item {:href (routes/organizations-explore)}
       "Explore"]
      [:a.item {:href (routes/organizations-owned)}
       "Owned"]
      [:a.item {:href (routes/organization-add)}
       "Add"]]
     [:div.ui.bottom.attached.segment
      [:div.ui.vertical.segment
       [:h1.ui.header "Organizations You're a Member of"]]
      [:div.ui.vertical.segment
       (if (seq organizations-joined)
         [:div.ui.divided.items
          (for [organization organizations-joined]
            ^{:key (:OrganizationId organization)}
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
               [action-button/component
                {:class "ui right floated small negative"}
                "Leave"
                (fn [callback]
                  (api/memberships-endpoint
                    :delete
                    organization
                    callback
                    nil))]]]])]
         [:p "You aren't a member of any organizations."])]]]))

(routes/register-page routes/organizations-chan #'page)
