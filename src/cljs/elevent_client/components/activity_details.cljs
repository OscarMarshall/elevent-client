;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.components.activity-details
  (:require [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]
            [clojure.string :refer [split-lines]]

            [elevent-client.locale :as locale]))

(defn component [activity]
  "The details that display beneath activities"
  [:div
   (when (:Location activity)
     [:div.meta [:strong "Location: "] (:Location activity)])
   (when activity
     [:div.meta [:strong "Time: "]
      (str (unparse locale/datetime-formatter
                    (from-string
                      (:StartTime activity)))
           " - "
           (unparse locale/datetime-formatter
                    (from-string
                      (:EndTime activity))))])
   (when (> (:TicketPrice activity) 0)
     [:div.meta
      [:strong "Ticket Price: "]
      (goog.string.format "$%.2f" (:TicketPrice activity))])
   (when (:Description activity)
     (for [line (split-lines (:Description activity))]
       ^{:key line}
       [:p line]))])
