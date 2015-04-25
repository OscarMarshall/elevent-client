(ns elevent-client.components.messages
  (:require [cljs.core.async :refer [put!]]

            [elevent-client.state :as state]))

(defn component []
  "Displays error and success messages at top of page"
  (when-let [messages* (seq @state/messages)]
    [:div.sixteen.wide.column
     ; Only display unique messages
     (for [[key [type message]] messages*]
       ^{:key key}
       [:div.ui.message {:class type}
        [:i.close.icon {:on-click #(put! state/remove-messages-chan key)}]
        message])]))
