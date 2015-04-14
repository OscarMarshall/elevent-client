(ns elevent-client.components.action-button
  (:require [reagent.core :as r :refer [atom]]))

; action must be a function that takes a callback for the API call as a param
(defn component [options text action & [alt-text]]
  (let [loading-text [:i.spinner.loading.icon]
        button-text  (atom text)]
    (fn [options text action & [alt-text alt-action]]
      [:div.ui.button
       (assoc options
         :on-click (fn []
                     (reset! button-text loading-text)
                     (if alt-text
                       (action #(reset! button-text alt-text))
                       (action #(reset! button-text text))))
         :type :button)
       @button-text])))
