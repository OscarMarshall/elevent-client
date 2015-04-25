;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.locale
  (:require [cljs-time.format :refer [formatter]]))

;; Date and Time formats
;; =============================================================================

(def date-format
  "The date format displayed to users."
  "MMMM d, yyyy")
(def time-format
  "The time format displayed to users."
  "h:mm a")
(def datetime-format
  "The datetime format displayed to users."
  (str time-format " " date-format))
(def input-date-time-format
  "The datetime format required for user inputs."
  "MM/dd/YYYY, hh:mm a")
(def moment-date-time-format
  "The datetime format required by the moment.js library."
  "MM/DD/YYYY, hh:mm a")
(def date-formatter
  "The cljs-time formatter for the date format."
  (formatter date-format))
(def time-formatter
  "The cljs-time formatter for the time format."
  (formatter time-format))
(def datetime-formatter
  "The cljs-time formatter for the datetime format."
  (formatter datetime-format))
(def input-date-time-formatter
  "The cljs-time formatter for the input datetime format."
  (formatter input-date-time-format))

(def input-date-time-re
  "The regular expression used to identify datetime inputs."
  #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d")
