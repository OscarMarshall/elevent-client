(ns elevent-client.locale
  (:require [cljs-time.format :refer [formatter]]))

;; Date and Time formats
;; =============================================================================

(def date-format "MMMM d, yyyy")
(def time-format "h:mm a")
(def datetime-format (str time-format " " date-format))
(def input-date-time-format "MM/dd/YYYY, hh:mm a")
(def moment-date-time-format "MM/DD/YYYY, hh:mm a")
(def date-formatter (formatter date-format))
(def time-formatter (formatter time-format))
(def datetime-formatter (formatter datetime-format))
(def input-date-time-formatter (formatter input-date-time-format))

(def input-date-time-re #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d")
