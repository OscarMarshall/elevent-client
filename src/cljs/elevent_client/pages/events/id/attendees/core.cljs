;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.events.id.attendees.core
  (:require [clojure.string :as str]
            [cljs.core.async :refer [put!]]
            [reagent.core :as r :refer [atom]]
            [ajax.core :refer [PUT]]
            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.authentication :as auth]
            [elevent-client.config :as config]
            [elevent-client.components.input :as input]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.components.paginator :as paginator]
            [elevent-client.pages.events.id.attendees.id.core
             :refer [check-in]]))

(defn page [event-id]
  "Attendees page"
  ; If you don't have user edit permissions for this event, don't show page.
  (if (and event-id
           (not (get-in (:EventPermissions (:permissions @state/session))
                        [event-id :EditUser])))
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:p "You do not have permission to view attendees for this event."]]]
  (let [form (atom {})
        page (atom 0)]
    (fn [event-id]
      (let
        [{:keys [email-filter last-name-filter first-name-filter group-filter]}
         @form

         event (into {} (d/entity @api/events-db event-id))

         groups
         (seq (d/q '[:find ?name ?group-id
                     :in $ ?event-id
                     :where
                     [?group-id :EventId ?event-id]
                     [?group-id :Name ?name]]
                   @api/groups-db
                   event-id))

         ; Create filter function with given keyword and which attribute to search
         create-filter (fn [[keywords attribute]]
                         #(or (empty? keywords)
                              (re-find
                                (re-pattern (str/lower-case keywords))
                                (str/lower-case (or (% attribute) "")))))

         ; Limits attendees by search filters
         passes-filters?
         (let [filters
               (apply juxt
                      (let [groups
                            (->> groups
                                 (filter (fn [[name]]
                                           (or (empty? group-filter)
                                               (re-find
                                                 (-> group-filter
                                                     str/lower-case
                                                     re-pattern )
                                                 (str/lower-case name)))))
                                 (map second)
                                 set)]
                        #(let [group (:GroupId %)]
                           (or (empty? group-filter) (groups group))))
                      (map create-filter
                           [[email-filter       :Email]
                            [last-name-filter   :LastName]
                            [first-name-filter  :FirstName]]))]
           #(every? identity (filters %)))

         attendees
         (->> (d/q '[:find ?e ?a
                     :in $ ?event-id
                     :where
                     [?a :EventId ?event-id]
                     [?a :UserId ?e]]
                   @api/attendees-db
                   event-id)
              (map (fn [[user-id attendee-id]]
                     (merge (into {} (d/entity @api/users-db
                                               user-id))
                            (into {} (d/entity @api/attendees-db
                                               attendee-id)))))
              (filter passes-filters?)
              (sort-by (juxt :LastName :FirstName)))

         ; Limit attendees to 10 per page
         paged-attendees
         (->> attendees
              (drop (* @page 10))
              (take 10)
              doall)]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:h2.ui.header
           "Attendees"]
          [:table.ui.table
           [:thead
            [:tr
             [:th "Email"]
             [:th "Last Name"]
             [:th "First Name"]
             [:th "Group"]
             [:th {:style {:width "130px"}}]
             [:th {:style {:width "125px"}}]]]
           [:tbody
            [:tr.ui.form
             ; Filters
             [:td
              [input/component
               :text
               {}
               (r/wrap email-filter swap! form assoc :email-filter)]]
             [:td
              [input/component
               :text
               {}
               (r/wrap last-name-filter swap! form assoc :last-name-filter)]]
             [:td
              [input/component
               :text
               {}
               (r/wrap first-name-filter swap! form assoc :first-name-filter)]]
             [:td
              [input/component
               :text
               {}
               (r/wrap group-filter swap! form assoc :group-filter)]]
             ; Get checked in count
             [:td {:style {:text-align :center}}
              (str (reduce #(if (:CheckinTime %2)
                              (inc %1)
                              %1)
                           0
                           attendees)
                   "/"
                   (count attendees))]]
            (for [attendee paged-attendees]
              ^{:key (:AttendeeId attendee)}
              [:tr
               [:td (:Email      attendee)]
               [:td (:LastName   attendee)]
               [:td (:FirstName  attendee)]
               [:td
                [input/component
                 :select
                 ; Don't allow editing groups if user does not have edit permissions
                 {:class (when-not (get-in (:EventPermissions (:permissions @state/session))
                                           [event-id :EditEvent])
                              "disabled")}
                 (cons ["None" 0] groups)
                 (r/wrap
                   (:GroupId attendee)
                   ; API call to add an attendee to group
                   (fn [x]
                     (let [uri (str config/https-url
                                    "/attendees/"
                                    (:AttendeeId attendee)
                                    "/groups/"
                                    x)]
                       (PUT
                         uri
                         {:timeout
                          8000

                          :headers
                          {:Authentication
                           (str "Bearer " (:token @state/session))}

                          :handler
                          #(api/attendees-endpoint :read nil nil nil)

                          :error-handler
                          (fn [error]
                            (if (= (:status error) 401)
                              (when (:token @state/session)
                                (put! state/add-messages-chan
                                      [:logged-out
                                       [:negative "Logged out"]])
                                (auth/sign-out!))
                              (if (= (:failure error) :timeout)
                                (put! state/add-messages-chan
                                      [(keyword "elevent-client.api"
                                                (str uri "-timed-out"))
                                       [:negative (str uri " timed out")]])
                                (put! state/add-messages-chan
                                      [(keyword "elevent-client.api" (gensym))
                                       [:negative
                                        (str uri (js->clj error))]]))))}))))]]
               [:td {:noWrap true}
                (if (:CheckinTime attendee)
                 [:button.ui.small.button.green
                  {:width "100%"}
                  "Checked in"]
                 [action-button/component
                  {:class "small"
                   :style {:width "100%"}}
                  "Check in"
                  (check-in (:AttendeeId attendee))])]
               [:td
                [:a.ui.right.floated.small.labeled.button
                 {:href (routes/event-attendee
                          {:EventId (:EventId event)
                           :AttendeeId (:AttendeeId attendee)})}
                 "Details"
                 [:i.right.chevron.icon]]]])]]
          [paginator/component attendees page]]])))))

(routes/register-page routes/event-attendees-chan #'page true)
