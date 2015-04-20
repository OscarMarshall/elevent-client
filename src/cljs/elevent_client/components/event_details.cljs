(ns elevent-client.components.event-details
  (:require [goog.string :as string]
            [cljs-time.core :refer [after?]]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]

            [elevent-client.locale :as locale]))

(defn component [event]
  [:div
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
   [:p (:Description event)]])
