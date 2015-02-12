(ns rest)

(defmacro endpoint [collection url element-id]
  (let [endpoint-symbol (symbol (str collection "-endpoint"))
        db-symbol       (symbol (str collection "-db"))]
    `(do
       (def ~collection (tailrecursion.javelin/cell []))
       (def ~endpoint-symbol
         (endpoint* ~url ~element-id ~collection))
       (def ~db-symbol
         (tailrecursion.javelin/cell=
           (datascript/db-with
             (datascript/empty-db)
             (map #(assoc % :db/id (~element-id %))
                  (map #(into {} (remove (comp nil? second) %))
                       ~collection)))))
       (~endpoint-symbol :read nil nil))))
