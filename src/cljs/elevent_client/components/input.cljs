(ns elevent-client.components.input
  (:require [reagent.core :as r]))

(defn component
  ([type options select-options state in out]
   (let [in  (or in  identity)
         out (or out identity)]
     (r/create-class
       {:component-did-mount
        (fn [this]
          (when (= type :select)
            (-> this
                r/dom-node
                js/jQuery
                (.dropdown (clj->js {:onChange #(when % (reset! state
                                                                (out %)))})))))

        :component-did-update
        (fn [this]
          (when (= type :select)
            (-> this
                r/dom-node
                js/jQuery
                (.dropdown (clj->js {:onChange #(when % (reset! state
                                                                (out %)))})))))

        :reagent-render
        (fn render
          ([_ options select-options state _ _]
           (let [attributes (assoc options
                              :value
                              (in @state)

                              :on-change
                              #(reset! state (out (.-value (.-target %)))))]
             (case type
               :textarea [:textarea attributes]
               :select [:div.ui.dropdown.selection
                        [:input (assoc attributes :type :hidden)]
                        [:div.text "None"]
                        [:i.dropdown.icon]
                        [:div.menu
                         (for [[name value] select-options]
                           ^{:key (or value 0)} [:div.item {:data-value value}
                                                 name])]]
               [:input (assoc attributes :type type)])))
          ([_ options state _ _]
           (render nil options nil state nil nil))
          ([_ options select-options state]
           (render nil options select-options state nil nil))
          ([_ options state]
           (render nil options nil state nil nil)))})))
  ([type options state in out]
   (component type options nil state in out))
  ([type options select-options state]
   (component type options select-options state nil nil))
  ([type options state]
   (component type options nil state nil nil)))
