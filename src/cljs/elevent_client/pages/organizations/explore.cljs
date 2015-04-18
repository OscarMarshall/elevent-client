(ns elevent-client.pages.organizations.explore
  (:require [clojure.set :as set]

            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.pages.organizations.core :as organizations]))

(defn page []
  (let [unjoined-organizations
        (map
          (partial d/entity @api/organizations-db)
          (set/difference
            (into #{} (d/q '[:find [?organization-id ...]
                             :where [?organization-id]]
                           @api/organizations-db))
            (into #{} (d/q '[:find [?organization-id ...]
                             :in $ ?user-id
                             :where
                             [?membership-id :UserId ?user-id]
                             [?membership-id :OrganizationId ?organization-id]]
                           @api/memberships-db
                           (get-in @state/session [:user :UserId])))))]
    [:div.sixteen.wide.column
     [organizations/tabs :explore]
     [:div.ui.bottom.attached.segment
      [:div.ui.vertical.segment
       [:h1.ui.header "Explore Organizations"]]
      [:div.ui.vertical.segment
       [:div.ui.divided.items
        (for [organization unjoined-organizations]
          ^{:key (:OrganizationId organization)}
          [:div.item
           [:div.content
            [:a.header
             (:Name organization)]
            [:div.extra
             [:a.ui.right.floated.small.button
              {:href (routes/events-explore
                       {:query-params (select-keys organization
                                                   [:OrganizationId])})} ; TODO: make this work
              "View events"
              [:i.right.chevron.icon]]
             [action-button/component
              {:class "ui right floated small"}
              "Join"
              (fn [callback]
                (api/memberships-endpoint
                  :create
                  (select-keys (merge (:user @state/session) organization)
                               [:UserId :OrganizationId])
                  callback
                  nil))]]]])]]]]))

(routes/register-page routes/organizations-explore-chan #'page)
