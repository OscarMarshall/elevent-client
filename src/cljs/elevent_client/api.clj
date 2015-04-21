(ns elevent-client.api)

(defmacro endpoints [& endpoints]
  `(do
     ~@(map (fn [[collection url element-id requires-token]]
              (let [endpoint-symbol (symbol (str collection "-endpoint"))
                    db-symbol       (symbol (str collection "-db"))]
                `(do
                   (def ~collection (reagent.core/atom []))
                   (def ~endpoint-symbol
                     (elevent-client.api/endpoint ~url
                                                  ~element-id
                                                  ~(symbol
                                                     "elevent-client.api"
                                                     (str collection))))
                   (def ~db-symbol (reagent.core/atom (datascript/empty-db)))
                   (add-watch ~(symbol "elevent-client.api" (str collection))
                              ~(keyword (gensym))
                              (fn [~'_ ~'_ ~'_ ~'elements]
                                (reset! ~(symbol "elevent-client.api"
                                                 (str db-symbol))
                                        (datascript/db-with
                                          (datascript/empty-db)
                                          (map (fn [~'x]
                                                 (assoc
                                                   (->> ~'x
                                                        (remove
                                                          (comp
                                                            (some-fn
                                                              nil?
                                                              #(when (coll? %)
                                                                 (empty? %)))
                                                            second))
                                                        (into {}))
                                                   :db/id (~element-id ~'x)))
                                               ~'elements))))))))
            endpoints)
     (defn ~'refresh! []
       ~@(map (fn [[collection _ _ requires-token]]
                `(if (or (not ~requires-token)
                         (:token @elevent-client.state/session))
                   (~(symbol "elevent-client.api" (str collection "-endpoint"))
                      :read nil nil)
                   (reset! ~(symbol collection) [])))
              endpoints))))
