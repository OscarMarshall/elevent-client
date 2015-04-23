(ns elevent-client.pages.organizations.owned
  (:require [reagent.core :refer [atom]]
            [datascript :as d]
            [clojure.string :as str]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.paginator :as paginator]
            [elevent-client.components.input :as input]
            [elevent-client.pages.organizations.core :as organizations]))

(defn page []
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [owned-organizations
            (->> (get-in @state/session [:permissions :OrganizationPermissions])
                 (filter (fn [[_ permissions]] (:EditOrganization permissions)))
                 (map #(into {} (d/entity @api/organizations-db (first %))))
                 (sort-by (comp str/lower-case :Name))
                 (filter #(when (seq %)
                            (re-find (re-pattern (str/lower-case @search))
                                     (str/lower-case (:Name %))))))
            paged-organizations
            (->> owned-organizations
                 (drop (* @page 10))
                 (take 10)
                 doall)]
        [:div.sixteen.wide.column
         [organizations/tabs :owned]
         [:div.ui.bottom.attached.segment
          [:div.ui.vertical.segment
           [:h1.ui.header "Organizations You Own"]]
          [:div.ui.vertical.segment
           [:div.ui.form
            [:div.field
             [:label "Search"]
             [input/component :text {} search]]]]
          [:div.ui.vertical.segment
           (if (seq owned-organizations)
             [:div.ui.divided.items
              (for [organization owned-organizations]
                ^{:key (:OrganizationId organization)}
                [:div.item
                 [:div.content
                  [:a.header {:href (routes/organization organization)}
                   (:Name organization)]
                  [:div.extra
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
                        #(api/permissions-endpoint
                           :read
                           nil
                           callback)
                        callback))]]]])]
             [:p "No organizations found"])]
          [:div.ui.vertical.segment
           [paginator/component owned-organizations page]]]]))))

(routes/register-page routes/organizations-owned-chan #'page true)
