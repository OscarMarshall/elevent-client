;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.components.logo
  (:require [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]))

(defn component [event-logo]
  "Logo column"
  (when (and @state/online? event-logo)
    [:div.three.wide.column
     [:div.ui.segment
      [:img {:style {:width "100%"}
             :src event-logo}]]]))
