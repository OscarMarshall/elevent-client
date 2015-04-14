(ns elevent-client.components.calendar
  (:require [reagent.core :as r :refer [atom]]))

(defn component [options]
  (let [options (atom {})]
    (r/create-class
      {:component-did-mount #(.fullCalendar (js/jQuery (r/dom-node %))
                                            (clj->js @options))
       :component-did-update #(.fullCalendar (js/jQuery (r/dom-node %))
                                             (clj->js @options))
       :reagent-render (fn [x]
                         (reset! options x)
                         [:div])})))
