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
            [elevent-client.components.input :as input]))

(defn page [event-id]
  (let [form (atom {})]
    (fn [event-id]
      (let
        [{:keys [email-filter last-name-filter first-name-filter group-filter]}
         @form

         event (into {} (d/entity @api/events-db event-id))

         attendees
         (sort-by (juxt :LastName :FirstName)
                  (map (fn [[user-id attendee-id]]
                         (merge (into {} (d/entity @api/users-db
                                                   user-id))
                                (into {} (d/entity @api/attendees-db
                                                   attendee-id))))
                       (d/q '[:find ?e ?a
                              :in $ ?event-id
                              :where
                              [?a :EventId ?event-id]
                              [?a :UserId ?e]]
                            @api/attendees-db
                            event-id)))

         groups
         (seq (d/q '[:find ?name ?group-id
                     :in $ ?event-id
                     :where
                     [?group-id :EventId ?event-id]
                     [?group-id :Name ?name]]
                   @api/groups-db
                   event-id))

         create-filter (fn [[keywords attribute]]
                         #(or (empty? keywords)
                              (re-find
                                (re-pattern (str/lower-case keywords))
                                (str/lower-case (or (% attribute) "")))))

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
           #(every? identity (filters %)))]
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
             [:th]]]
           [:tbody
            [:tr.ui.form
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
             [:td {:style {:text-align :center}}
              (str (reduce #(if (:CheckinTime %2)
                              (inc %1)
                              %1)
                           0
                           attendees)
                   "/"
                   (count attendees))]]
            (for [attendee attendees]
              ^{:key (:AttendeeId attendee)}
              [:tr {:style {:display (when-not (passes-filters? attendee)
                                       :none)}}
               [:td (:Email      attendee)]
               [:td (:LastName   attendee)]
               [:td (:FirstName  attendee)]
               [:td
                [input/component
                 :select
                 {}
                 groups
                 (r/wrap
                   (:GroupId attendee)
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
                [:a.ui.right.floated.small.labeled.button
                 {:class (when (:CheckinTime attendee) :green)
                  :style {:width "100%"}
                  :href (routes/event-attendee
                          {:EventId event-id
                           :AttendeeId (:AttendeeId attendee)})}
                 (if (:CheckinTime attendee)
                   "Checked in"
                   "Check in")]]])]]]]))))

(routes/register-page routes/event-attendees-chan #'page true)
