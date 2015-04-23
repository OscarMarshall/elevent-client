(ns elevent-client.components.paginator
  (:require [reagent.core :refer [atom]]))

(defn component [coll state]
  (let [pages (js/Math.ceil (/ (count coll) 10))]
    [:div.ui.pagination.menu
     [:a.icon.item {:on-click (fn [] (swap! state #(max (dec %) 0)))
                    :class    (when (= @state 0) "disabled")}
      [:i.left.arrow.icon]]
     (doall (for [page (range pages)]
              ^{:key page}
              [:a.item {:on-click #(reset! state page)
                        :class    (when (= @state page) "active")}
               (inc page)]))
     [:a.icon.item {:on-click (fn [] (swap! state #(min (inc %) (dec pages))))
                    :class    (when (= @state (max (dec pages) 0)) "disabled")}
      [:i.right.arrow.icon]]]))
