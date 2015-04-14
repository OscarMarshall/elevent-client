(ns elevent-client.components.event-details
  (:require [cljs-time.core :refer [after?]]
            [cljs-time.coerce :refer [from-string]]
            [cljs-time.format :refer [unparse]]

            [elevent-client.locale :as locale]))

(defn component [event]
  [:div
   [:div
    [:b "Date: "]
    (when (and (:StartDate event)
               (:EndDate event))
      (let [start (from-string (:StartDate event))
            end   (from-string (:EndDate   event))]
        (str (unparse locale/datetime-formatter start)
             (when (after? end start)
               (str " to "
                    (unparse locale/datetime-formatter end))))))]
   [:div
    [:b "Venue: "] (:Venue event)]
   [:p (:Description event)]])
