(ns util)

(def clj->json (comp js/JSON.stringify clj->js))
