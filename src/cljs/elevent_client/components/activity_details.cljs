(ns elevent-client.components.activity-details
  (:require [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]

            [elevent-client.locale :as locale]))

(defn component [activity]
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
   [:div.description
    (:Description activity)]])
