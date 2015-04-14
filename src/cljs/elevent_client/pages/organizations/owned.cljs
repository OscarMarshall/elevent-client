(ns elevent-client.pages.organizations.owned
  (:require [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]))

;TODO: Display only organizations you own
(defn page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    [:div.ui.vertical.segment
     [:h1.ui.header
      "Organizations You Own"
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
           [:a.ui.right.floated.small.icon.button
            {:href (str "#/events?organization="
                        (:OrganizationId organization))} ; TODO: make this work
            "View events"]]]])]]]])

(routes/register-page routes/organizations-owned-chan #'page)
