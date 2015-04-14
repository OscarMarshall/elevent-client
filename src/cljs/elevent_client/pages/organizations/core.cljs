(ns elevent-client.pages.organizations.core
  (:require [datascript :as d]
            [elevent-client.routes :as routes]
            [elevent-client.api :as api]))

;TODO: Display only organizations you're a member of
(defn page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    [:div.ui.vertical.segment
     [:h1.ui.header
      "Organizations You're a Member of"
      [:a.ui.right.floated.small.button
       {:href (routes/organization-add)}
       "Add Organization"]]]
    [:div.ui.vertical.segment
     [:div.ui.divided.items
      (for [organization (doall (map #(d/entity @api/organizations-db
                                                %)
                                     (d/q '[:find [?organization-id ...]
                                            :where [?organization-id]]
                                          @api/organizations-db)))]
        ^{:key (:OrganizationId organization)}
        [:div.item
         [:div.content
          [:a.header
           (:Name organization)]
          [:div.extra
           ; TODO: make this work
           [:a.ui.right.floated.small.icon.button
            {:href (routes/events-explore {:query-params
                                          (select-keys organization
                                                       [:OrganizationId])})}
            "View events"]]]])]]]])

(routes/register-page routes/organizations-chan #'page)
