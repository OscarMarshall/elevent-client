(ns elevent-client.components.chart
  (:require [reagent.core :as r :refer [atom]]))

(defn component [config _]
  "Initialize Highchart graphs with config and series data"
  (let [data       (atom nil)
        ; Bar charts need two series: labels and values
        ; If this is a bar chart, split data
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
       ; If data updates, set data (and categories) for chart
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
         ; Placeholder for graph
         [:div])})))
