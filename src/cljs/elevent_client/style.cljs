;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.style
  (:require [garden.core :refer [css]]))

;; Stylesheet
;; =============================================================================

(defn stylesheet
  "Reagent component which defines site-specific CSS. This component is mounted
  in the app-frame component."
  []
  [:style (css [:.center {:display      "block"
                          :margin-left  "auto"
                          :margin-right "auto"}]
               [:.menu [:.logo.item {:padding-top    ".32em"
                                     :padding-bottom ".32em"}
                        [:img {:height "2em"}]]]
               [:.ui.vertical.segment:first-child {:padding-top 0}]
               [:.ui.vertical.segment:last-child {:padding-bottom 0}])])
