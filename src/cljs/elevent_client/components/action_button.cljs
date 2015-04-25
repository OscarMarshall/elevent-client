(ns elevent-client.components.action-button
  (:require [reagent.core :as r :refer [atom]]))

(defn component [options text action & [alt-text]]
  "Component for all buttons that make API calls so they show as loading
   Action must be a function that takes a callback for the API call as a param"
  (let [loading-text [:i.spinner.loading.icon]
        button-text  (atom text)]
    (fn [options text action & [alt-text alt-action]]
      [:button.ui.button
       (assoc options
         :on-click (fn [e]
                     (.preventDefault e)
                     (reset! button-text loading-text)
                     (if alt-text
                       (action #(reset! button-text alt-text))
                       (action #(reset! button-text text)))))
       @button-text])))
