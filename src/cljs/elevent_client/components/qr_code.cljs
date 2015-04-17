(ns elevent-client.components.qr-code
  (:require [reagent.core :as r :refer [atom]]))

(defn component [options]
  (let [options (atom {})]
    (r/create-class
      {:component-did-mount #(.qrcode (js/jQuery (r/dom-node %))
                                      (clj->js @options))
       :component-did-update #(.qrcode (js/jQuery (r/dom-node %))
                                       (clj->js @options))
       :reagent-render (fn [x]
                         (reset! options x)
                         [:div])})))
