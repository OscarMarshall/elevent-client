(ns elevent-client.pages.organizations.core
  (:require [reagent.core :refer [atom]]
            [datascript :as d]
            [clojure.string :as str]
            [elevent-client.routes :as routes]
            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.input :as input]
            [elevent-client.components.paginator :as paginator]))

(defn tabs [page]
  (let [logged-in? (:token @state/session)]
    [:div.ui.top.attached.tabular.menu
     (when logged-in?
       [:a.item {:href (routes/organizations) :class (when (= page :core)
                                                       "active")}
        "Organizations"])
     [:a.item {:href (routes/organizations-explore)
               :class (when (= page :explore) "active")}
      "Explore"]
     (when logged-in?
       [:a.item {:href (routes/organizations-owned) :class (when (= page :owned)
                                                             "active")}
        "Owned"])
     (when logged-in?
       [:a.item {:href (routes/organization-add) :class (when (= page :add)
                                                          "active")}
        "Add"])]))

(defn page []
  (let [search (atom "")
        page (atom 0)]
    (fn []
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
                 (filter #(when (:Name %)
                            (re-find (re-pattern (str/lower-case @search))
                                     (str/lower-case (:Name %)))))
                 (sort-by (comp str/lower-case :Name)))
            paged-organizations
            (->> organizations-joined
                 (drop (* @page 10))
                 (take 10)
                 doall)]
        [:div.sixteen.wide.column
         [tabs :core]
         [:div.ui.bottom.attached.segment
          [:div.ui.vertical.segment
           [:h1.ui.header "Organizations You're a Member of"]]
          [:div.ui.vertical.segment
           [:div.ui.form
            [:div.field
             [:label "Search"]
             [input/component :text {} search]]]]
          [:div.ui.vertical.segment
           (if (seq organizations-joined)
             [:div.ui.divided.items
              (for [organization paged-organizations]
                ^{:key (:OrganizationId organization)}
                [:div.item
                 [:div.content
                  [:a.header {:href (routes/organization organization)}
                   (:Name organization)]
                  [:div.extra
                   [action-button/component
                    {:class "ui right floated small negative"}
                    "Leave"
                    (fn [callback]
                      (api/memberships-endpoint
                        :delete
                        organization
                        callback
                        callback))]]]])]
             [:p "No organizations found"])]
          [:div.ui.vertical.segment
           [paginator/component
            organizations-joined
            page]]]]))))

(routes/register-page routes/organizations-chan #'page true)
