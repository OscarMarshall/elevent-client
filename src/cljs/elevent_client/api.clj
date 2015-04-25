;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.api)

(defmacro endpoints
  "Given a variable amount of vectors containing a symbol, uri, ID keyword, and
  a boolean stating if the user must be logged in to use the endpoint, defines
  a local storage backed atom, an endpoint, and a datascript database for each
  vector. Also defines the function refresh! which will refresh all of the
  endpoints data."
  [& endpoints]
  `(do
     ~@(map (fn [[collection url element-id requires-token]]
              (let [endpoint-symbol (symbol (str collection "-endpoint"))
                    db-symbol       (symbol (str collection "-db"))]
                `(do
                   (def ~collection (alandipert.storage-atom/local-storage
                                      (reagent.core/atom [])
                                      ~(keyword collection)))
                   (def ~endpoint-symbol
                     (elevent-client.api/endpoint ~url
                                                  ~element-id
                                                  ~(symbol
                                                     "elevent-client.api"
                                                     (str collection))))
                   (def ~db-symbol
                     (reagent.core/atom
                       (datascript/db-with (datascript/empty-db)
                                           (map #(assoc
                                                   (->> %
                                                        (remove (comp nil? second))
                                                        (into {}))
                                                   :db/id (~element-id %))
                                                @~collection))))
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
