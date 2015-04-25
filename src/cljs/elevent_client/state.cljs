;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.state
  (:require
    [cljs.core.async :refer [<! chan]]
    [reagent.core :as r :refer [atom]]
    [alandipert.storage-atom :refer [local-storage]])
  (:require-macros [cljs.core.async.macros :refer [go-loop]]))

;; Online
;; =============================================================================

(def online?
  "Atom which stores a boolean indicating the client is online."
  (atom js/window.navigator.onLine))
(js/window.addEventListener "online" #(reset! online? true))
(js/window.addEventListener "offline" #(reset! online? false))


;; Location
;; =============================================================================

(defonce location
  "Atom which stores the current location hash fragment."
  (atom ""))
(defonce current-page
  "Atom which stores the Reagent component for the current page."
  (atom nil))


;; Session
;; =============================================================================
;;
;; An atom that stores state which should be persisted in LocalStorage

(def session
  "Atom synced with local storage that holds current session values such as the
  login token and user data. Think of it as cookies for SPA's."
  (local-storage (atom {}) :session))


;; Messages
;; =============================================================================

(defonce messages
  "Map from arbitrary identifiers to messages. The messages are displayed at the
  top of the site (right under the navbar and breadcrumbs) and are used to give
  the user feedback."
  (atom {}))
(def add-messages-chan
  "Channel which will add any two value sequence put on it to the messages map.
  The first value will be interpreted as the key and the second the value. If a
  message already exists with the specified key, the old message will be
  replaced by the incoming message."
  (chan))
(go-loop []
  (apply swap! messages assoc (<! add-messages-chan))
  (recur))
(def remove-messages-chan
  "Channel which will remove the specified key from the messages map. When the
  key is not present in the map, nothing happens."
  (chan))
(go-loop []
  (swap! messages dissoc (<! remove-messages-chan))
  (recur))


;; Channels
;; =============================================================================

(def api-refresh-chan
  "Channel used to notify the API that it should refresh all of the databases."
  (chan))
(def auth-sign-up-chan
  "Channel used by the Authentication system to notify the API that it should
  add a new user using the params put on the channel."
  (chan))
