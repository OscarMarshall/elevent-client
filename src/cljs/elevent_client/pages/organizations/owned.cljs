(ns elevent-client.pages.organizations.owned
  (:require [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]))

;TODO: Display only organizations you own
(defn page []
  (let [owned-organizations
        (doall (map (partial d/entity @api/organizations-db)
                    (d/q '[:find [?organization-id ...]
                           :in $ ?user-id
                           :where
                           [?organization-id :AdminId ?user-id]]
                         @api/organizations-db
                         (get-in @state/session [:user :UserId]))))]
    [:div.sixteen.wide.column
     [:div.ui.top.attached.tabular.menu
      [:a.item {:href (routes/organizations)}
       "Organizations"]
      [:a.item {:href (routes/organizations-explore)}
       "Explore"]
      [:a.active.item {:href (routes/organizations-owned)}
       "Owned"]
      [:a.item {:href (routes/organization-add)}
       "Add"]]
     [:div.ui.bottom.attached.segment
      [:div.ui.vertical.segment
       [:h1.ui.header "Organizations You Own"]]
      [:div.ui.vertical.segment
       [:div.ui.divided.items
        (for [organization owned-organizations]
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
                  callback
                  nil))]]]])]]]]))

(routes/register-page routes/organizations-owned-chan #'page)
