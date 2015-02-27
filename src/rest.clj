(ns rest)

(defmacro endpoints [& endpoints]
  `(do
     ~@(map (fn [[collection url element-id requires-token]]
              (let [endpoint-symbol (symbol (str collection "-endpoint"))
                    db-symbol       (symbol (str collection "-db"))]
                `(do
                   (def ~collection (tailrecursion.javelin/cell []))
                   (def ~endpoint-symbol
                     (endpoint ~url ~element-id ~collection ~requires-token))
                   (def ~db-symbol
                     (tailrecursion.javelin/cell=
                       (datascript/db-with
                         (datascript/empty-db)
                         (map #(assoc % :db/id (~element-id %))
                              (map #(into {} (remove (comp nil? second) %))
                                   ~collection))))))))
            endpoints)
     (defn ~'refresh []
       ~@(map (fn [[collection _ _ requires-token]]
                `(if (or (not ~requires-token) @app/token)
                   (~(symbol (str collection "-endpoint")) :read nil nil)
                   (reset! ~(symbol collection) [])))
              endpoints))
     (~'refresh)))
