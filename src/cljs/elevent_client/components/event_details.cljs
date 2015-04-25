;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.components.event-details
  (:require [goog.string :as string]
            [clojure.string :refer [split-lines]]
            [datascript :as d]
            [cljs-time.core :refer [after?]]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]

            [elevent-client.api :as api]
            [elevent-client.locale :as locale]))

(defn component [event]
  "The details that display beneath events"
  [:div
   ; Show organization if available
   (when (> (:OrganizationId event) 0)
     (when-let [org (into {} (d/entity @api/organizations-db (:OrganizationId event)))]
       [:div
        [:b "Organization: "] (:Name org)]))
   (when (and (:StartDate event)
              (:EndDate event))
     [:div
      [:b "Date: "]
      (let [start (from-string (:StartDate event))
            end   (from-string (:EndDate   event))]
        (str (unparse locale/datetime-formatter start)
             (when (after? end start)
               (str " to "
                    (unparse locale/datetime-formatter end)))))])
   (when (:Venue event)
     [:div
      [:b "Venue: "] (:Venue event)])
   (when (> (:TicketPrice event) 0)
     [:div.meta
      [:strong "Ticket Price: "]
      (string/format "$%.2f" (:TicketPrice event))])
   (when (:Description event)
     (for [line (split-lines (:Description event))]
       ^{:key line}
       [:p line]))])
