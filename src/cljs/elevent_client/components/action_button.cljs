(ns elevent-client.components.action-button
  (:require [reagent.core :as r :refer [atom]]))

; action must be a function that takes a callback for the API call as a param
(defn component [options text action & [alt-text]]
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
