(ns elevent-client.core)

(defmacro endpoints [& endpoints]
  `(do
     ~@(mapcat (fn [[collection url element-id requires-token]]
                 (let [endpoint-symbol (symbol (str collection "-endpoint"))
                       db-symbol       (symbol (str collection "-db"))]
                   `((def ~collection (reagent.core/atom []))
                     (def ~endpoint-symbol
                       (elevent-client.core/endpoint ~url
                                                     ~element-id
                                                     ~(symbol
                                                        "elevent-client.core"
                                                        (str collection))))
                     (def ~db-symbol (reagent.core/atom (datascript/empty-db)))
                     (add-watch ~(symbol "elevent-client.core" (str collection))
                                ~(keyword (gensym))
                                (fn [~'_ ~'_ ~'_ ~'elements]
                                  (reset! ~(symbol "elevent-client.core"
                                                   (str db-symbol))
                                          (datascript/db-with
                                            (datascript/empty-db)
                                            (map #(assoc
                                                    (->> %
                                                         (remove (comp nil?
                                                                       second))
                                                         (into {}))
                                                    :db/id (~element-id %))
                                                 ~'elements))))))))
                 endpoints)
     (defn ~'refresh! []
       ~@(map (fn [[collection _ _ requires-token]]
                `(if (or (not ~requires-token)
                         (:token @elevent-client.core/session))
                   (~(symbol "elevent-client.core" (str collection "-endpoint"))
                      :read nil nil)
                   (reset! ~(symbol collection) [])))
              endpoints))
     (~'refresh!)))
