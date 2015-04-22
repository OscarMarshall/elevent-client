(ns elevent-client.state
  (:require
    [cljs.core.async :refer [<! chan]]
    [reagent.core :as r :refer [atom]]
    [alandipert.storage-atom :refer [local-storage]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

;; Online
;; =============================================================================

(def online? (atom js/window.navigator.onLine))
(js/window.addEventListener "online" #(reset! online? true))
(js/window.addEventListener "offline" #(reset! online? false))


;; Location
;; =============================================================================

(defonce location (atom ""))
(defonce current-page (atom nil))


;; Session
;; =============================================================================
;;
;; An atom that stores state which should be persisted in LocalStorage

(def session (local-storage (atom {}) :session))


;; Messages
;; =============================================================================

(defonce messages (atom {}))
(def add-messages-chan (chan))
(go-loop []
  (apply swap! messages assoc (<! add-messages-chan))
  (recur))
(def remove-messages-chan (chan))
(go-loop []
  (swap! messages dissoc (<! remove-messages-chan))
  (recur))


;; Channels
;; =============================================================================

(def api-refresh-chan (chan))
(def auth-sign-up-chan (chan))
