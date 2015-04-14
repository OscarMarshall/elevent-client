(ns elevent-client.pages.events.id.attendees.core
  (:require [clojure.string :as str]
            [reagent.core :as r :refer [atom]]
            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.components.input :as input]))

(defn page [event-id]
  (let [form (atom {:EventId event-id})]
    (fn [event-id]
      (let
        [{:keys [email-filter last-name-filter first-name-filter]}
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

         create-filter (fn [[keywords attribute]]
                         #(or (empty? keywords)
                              (re-find
                                (re-pattern (str/lower-case keywords))
                                (str/lower-case (or (% attribute) "")))))

         passes-filters?
         #(every? identity
                  ((apply juxt (map create-filter
                                    [[email-filter       :Email]
                                     [last-name-filter   :LastName]
                                     [first-name-filter  :FirstName]])) %))]
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

(routes/register-page routes/event-attendees-chan #'page)
