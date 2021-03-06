;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.core
  (:require [goog.events :as events]
            [goog.history.EventType :as EventType]

            [cljsjs.react]
            [reagent.core :as r :refer [atom]]

            [elevent-client.state :as state]
            [elevent-client.routes :as routes]
            [elevent-client.style :as style]

            [elevent-client.components.breadcrumbs :as breadcrumbs]
            [elevent-client.components.messages :as messages]
            [elevent-client.components.navbar :as navbar]

            [elevent-client.pages.home]
            [elevent-client.pages.sign-in]
            [elevent-client.pages.sign-up]
            [elevent-client.pages.password-reset]
            [elevent-client.pages.forgot-password]
            [elevent-client.pages.calendar]
            [elevent-client.pages.statistics]
            [elevent-client.pages.payments]
            [elevent-client.pages.events.core]
            [elevent-client.pages.events.explore]
            [elevent-client.pages.events.owned]
            [elevent-client.pages.events.id.core]
            [elevent-client.pages.events.id.edit]
            [elevent-client.pages.events.id.register]
            [elevent-client.pages.events.id.schedule]
            [elevent-client.pages.events.id.activities.id.core]
            [elevent-client.pages.events.id.activities.id.edit]
            [elevent-client.pages.events.id.attendees.core]
            [elevent-client.pages.events.id.attendees.id.core]
            [elevent-client.pages.events.id.groups.id.edit]
            [elevent-client.pages.events.id.groups.id.core]
            [elevent-client.pages.organizations.core]
            [elevent-client.pages.organizations.explore]
            [elevent-client.pages.organizations.owned]
            [elevent-client.pages.organizations.id.core]
            [elevent-client.pages.organizations.id.edit])
  (:import goog.History))


(.initializeTouchEvents js/React true)


;; Frame
;; =============================================================================

(defn app-frame
  "Reagent component that puts together the top-level Reagent compenents."
  []
  ^{:key @state/current-page}
  [:div
   [style/stylesheet]
   [navbar/component]
   [:div.ui.page.grid
    [breadcrumbs/component]
    [messages/component]
    @state/current-page]])


;; History
;; =============================================================================

; must be called after routes have been defined
(defn hook-browser-navigation!
  "Uses goog.history to listen for page navigate events and calls
  routes/dispatch! with the hash fragment."
  []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (reset! state/messages {})
       (let [token (apply str (or (seq (.-token event)) '("/")))]
         (reset! state/location (str "#" token))
         (routes/dispatch! token))))
    (.setEnabled true)))


;; Initialize app
;; =============================================================================

(defn mount-root
  "Mounts the app-frame as the DOM body."
  []
  (r/render [app-frame] (.-body js/document)))

(defn init!
  "Initialize the navigation listener and mount the app-frame."
  []
  (hook-browser-navigation!)
  (mount-root))
