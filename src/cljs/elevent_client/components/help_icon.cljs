(ns elevent-client.components.help-icon
  (:require [reagent.core :as r]))

(defn component [text & [icon]]
  "Help icon with popup text
   If icon is not specified, defaults to question mark"
  (r/create-class
    {:component-did-mount
     #(-> %
          r/dom-node
          js/jQuery
          (.popup))
     :reagent-render
     (fn []
       (if icon
         [icon {:data-content text}]
         [:i.help.circle.icon.link {:data-content text
                                    :data-variation "inverted"}]))}))
