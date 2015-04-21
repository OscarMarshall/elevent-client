(ns elevent-client.components.help-icon
  (:require [reagent.core :as r]))

(defn component [text]
  (r/create-class
    {:component-did-mount
     #(-> %
          r/dom-node
          js/jQuery
          (.popup))
     :reagent-render
     (fn []
       [:i.help.circle.icon.link {:data-content text
                                  :data-variation "inverted"}])}))
