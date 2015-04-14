(ns elevent-client.components.chart
  (:require [reagent.core :as r :refer [atom]]))

(defn component [config _]
  (let [data       (atom nil)
        split-data (fn [data]
                     (if (seq data)
                       (if (= (:type (:chart config)) "bar")
                         [(into [] (map second data)) (map first data)]
                         [data nil])
                       [nil nil]))]
    (r/create-class
      {:component-did-mount
       #(let [[series-data categories] (split-data @data)]
          (.highcharts (js/jQuery (r/dom-node %))
                       (clj->js (assoc-in
                                  (assoc-in config
                                            [:series 0 :data] series-data)
                                  [:xAxis :categories] categories))))
       :component-did-update
       #(let [[series-data categories] (split-data @data)
              chart (-> %
                        r/dom-node
                        js/jQuery
                        .highcharts)]
          (-> chart
              .-series
              first
              (.setData (clj->js series-data)))
          (-> chart
              .-xAxis
              first
              (.setCategories (clj->js categories))))
       :reagent-render
       (fn [_ series]
         (reset! data series)
         [:div])})))
