(ns elevent-client.pages.organizations.explore
  (:require [reagent.core :refer [atom]]
            [clojure.set :as set]
            [clojure.string :as str]

            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.input :as input]
            [elevent-client.components.paginator :as paginator]
            [elevent-client.pages.organizations.core :as organizations]))

(defn page []
  (let [search (atom "")
        page (atom 0)]
    (fn []
      (let [unjoined-organizations
            (->> (set/difference
                   (into #{} (d/q '[:find [?organization-id ...]
                                    :where [?organization-id]]
                                  @api/organizations-db))
                   (into #{} (d/q '[:find [?organization-id ...]
                                    :in $ ?user-id
                                    :where
                                    [?membership-id :UserId ?user-id]
                                    [?membership-id :OrganizationId ?organization-id]]
                                  @api/memberships-db
                                  (get-in @state/session [:user :UserId]))))
                 (map (partial d/entity @api/organizations-db))
                 (filter #(when (seq %)
                            (re-find (re-pattern (str/lower-case @search))
                                     (str/lower-case (:Name %)))))
                 (sort-by (comp str/lower-case :Name)))
            paged-organizations
            (->> unjoined-organizations
                 (drop (* @page 10))
                 (take 10)
                 doall)]
        [:div.sixteen.wide.column
         [organizations/tabs :explore]
         [:div.ui.bottom.attached.segment
          [:div.ui.vertical.segment
           [:h1.ui.header "Explore Organizations"]]
          [:div.ui.vertical.segment
           [:div.ui.form
            [:div.field
             [:label "Search"]
             [input/component :text {} search]]]]
          [:div.ui.vertical.segment
           (if (seq unjoined-organizations)
             [:div.ui.divided.items
              (for [organization paged-organizations]
                ^{:key (:OrganizationId organization)}
                [:div.item
                 [:div.content
                  [:a.header {:href (routes/organization organization)}
                   (:Name organization)
                   (when (:token @state/session)
                     [action-button/component
                      {:class "ui right floated small"}
                      "Join"
                      (fn [callback]
                        (api/memberships-endpoint
                          :create
                          (select-keys (merge (:user @state/session) organization)
                                       [:UserId :OrganizationId])
                          #(do
                             (callback)
                             (js/location.assign (routes/organizations)))
                          callback))])]]])]
             [:p "No organizations found"])]
          [:div.ui.vertical.segment
           [paginator/component
            unjoined-organizations
            page]]]]))))

(routes/register-page routes/organizations-explore-chan #'page)
