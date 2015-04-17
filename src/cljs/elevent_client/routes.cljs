(ns elevent-client.routes
  (:require [cljs.core.async :refer [<! chan put!]]
            [secretary.core :as secretary :refer-macros [defroute]]

            [elevent-client.state :as state])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

;; Routes
;; =============================================================================

(def home-chan (chan))
(def sign-in-chan (chan))
(def sign-up-chan (chan))
(def password-reset-chan (chan))
(def forgot-password-chan (chan))
(def events-chan (chan))
(def events-explore-chan (chan))
(def events-owned-chan (chan))
(def event-chan (chan))
(def event-edit-chan (chan))
(def event-register-chan (chan))
(def event-activities-chan (chan))
(def event-activities-explore-chan (chan))
(def event-activity-chan (chan))
(def event-activity-add-chan (chan))
(def event-activity-edit-chan (chan))
(def event-schedule-chan (chan))
(def event-attendees-chan (chan))
(def event-attendee-chan (chan))
(def organizations-chan (chan))
(def organizations-explore-chan (chan))
(def organizations-owned-chan (chan))
(def organization-chan (chan))
(def organization-edit-chan (chan))
(def calendar-chan (chan))
(def statistics-chan (chan))
(def payments-chan (chan))


(secretary/set-route-prefix! "#")

(defroute home
  "/" []
  (put! home-chan []))

(defroute sign-in
  "/sign-in" []
  (put! sign-in-chan []))

(defroute sign-up
  "/sign-up" []
  (put! sign-up-chan []))

(defroute password-reset
  "/password-reset" {:keys [params query-params]}
  (put! password-reset-chan [(:id query-params)]))

(defroute forgot-password
  "/forgot-password" []
  (put! forgot-password-chan []))

(defroute events-explore
  "/events/explore" []
  (put! events-explore-chan []))

(defroute events-owned
  "/events/owned" []
  (put! events-owned-chan []))

(defroute events
  "/events" []
  (put! events-chan []))

(defroute event-add
  "/events/add" []
  (put! event-edit-chan []))

(defroute event
  "/events/:EventId" [EventId]
  (put! event-chan [(int EventId)]))

(defroute event-edit
  "/events/:EventId/edit" [EventId]
  (put! event-edit-chan [(int EventId)]))

(defroute event-register
  "/events/:EventId/register" [EventId]
  (put! event-register-chan [(int EventId)]))

(defroute event-activities
  "/events/:EventId/activities" [EventId]
  (put! event-activities-chan [(int EventId)]))

(defroute event-activities-explore
  "/events/:EventId/activities/explore" [EventId]
  (put! event-activities-explore-chan [(int EventId)]))

(defroute event-activity-add
  "/events/:EventId/activities/add" [EventId]
  (put! event-activity-edit-chan [(int EventId)]))

(defroute event-schedule
  "/events/:EventId/schedule" [EventId]
  (put! event-schedule-chan [(int EventId)]))

(defroute event-activity
  "/events/:EventId/activities/:ActivityId" [EventId ActivityId]
  (put! event-activity-chan [(int EventId) (int ActivityId)]))

(defroute event-activity-edit
  "/events/:EventId/activities/:ActivityId/edit" [EventId ActivityId]
  (put! event-activity-edit-chan [(int EventId) (int ActivityId)]))

(defroute event-attendees
  "/events/:EventId/attendees" [EventId]
  (put! event-attendees-chan [(int EventId)]))

(defroute event-attendee
  "/events/:EventId/attendees/:AttendeeId" [EventId AttendeeId]
  (put! event-attendee-chan [(int EventId) (int AttendeeId)]))

(defroute organizations
  "/organizations" []
  (put! organizations-chan []))

(defroute organizations-explore
  "/organizations/explore" []
  (put! organizations-explore-chan []))

(defroute organizations-owned
  "/organizations/owned" []
  (put! organizations-owned-chan []))

(defroute organization-add
  "/organizations/add" []
  (put! organization-edit-chan []))

(defroute organization
  "/organizations/:OrganizationId" [OrganizationId]
  (put! organization-chan [(int OrganizationId)]))

(defroute organization-edit
  "/organizations/:OrganizationId/edit" [OrganizationId]
  (put! organization-edit-chan [(int OrganizationId)]))

(defroute calendar
  "/calendar" []
  (put! calendar-chan []))

(defroute statistics
  "/statistics" []
  (put! statistics-chan []))

(defroute payments
  "/payments" []
  (put! payments-chan []))

(def dispatch!
  (secretary/uri-dispatcher [home
                             sign-in
                             sign-up
                             password-reset
                             forgot-password
                             events-explore
                             events-owned
                             events
                             event-add
                             event
                             event-edit
                             event-register
                             event-activities-explore
                             event-activities
                             event-activity-add
                             event-activity
                             event-activity-edit
                             event-schedule
                             event-attendees
                             event-attendee
                             organizations
                             organizations-explore
                             organizations-owned
                             organization-add
                             organization
                             organization-edit
                             calendar
                             statistics
                             payments]))

(defn register-page [channel page]
  (go-loop []
    (reset! state/current-page (into [] (cons page (<! channel))))
    (recur)))
