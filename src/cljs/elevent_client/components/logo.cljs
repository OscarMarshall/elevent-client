(ns elevent-client.components.logo
  (:require [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.state :as state]
            [elevent-client.locale :as locale]
            [elevent-client.routes :as routes]))

(defn component [event-logo]
  (when (and @state/online? event-logo)
    [:div.three.wide.column
     [:div.ui.segment
      [:img {:style {:width "100%"}
             :src event-logo}]]]))
