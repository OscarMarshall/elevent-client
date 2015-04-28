;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.api
  (:require [ajax.core :refer [DELETE GET POST PUT]]
            [cljs.core.async :as async :refer [put! chan <!]]
            [datascript :as d]

            [elevent-client.state :as state]
            [elevent-client.authentication :as auth]
            [elevent-client.config :as config])
  (:require-macros [elevent-client.api :refer [endpoints]]
                   [cljs.core.async.macros :refer [go-loop]]))

;; REST
;; =============================================================================

(defn api-call
  "Calls the corresponding cljs-ajax function on the uri with the specified
  params and sane defaults. The specified handler function will be called on
  success and the specified error-handler will be called on failure."
  [op uri params handler & [error-handler]]
  (let [options {:format          :json
                 :response-format (when (= op :read) :json)
                 :keywords?       true
                 :timeout         8000
                 :headers
                 (if (:token @state/session)
                   {:Authentication
                    (str "Bearer " (:token @state/session))}
                   {})
                 :params params
                 :handler handler
                 :error-handler error-handler}
        url (str config/https-url uri)]
    (case op
      :create (POST url options)
      :update (PUT url options)
      :read   (GET url options)
      :delete (DELETE url options))))

(defn endpoint
  "Generates an endpoint function with the given uri, element-id, and state
  atom. The resulting function will take a CRUD operation keyword, body params,
  and handlers, and calls the corresponding cljs-ajax function on the uri. If
  the :read operation was specified, the state atom is updated with the
  response, else the endpoint is recursively called with the :read operation."
  [uri element-id state]
  (fn dispatch!
    ([operation params handler error-handler]
     (let [options {:format          :json

                    :response-format (when (= operation :read) :json)

                    :keywords?       true

                    :timeout         8000

                    :headers
                    (if (:token @state/session)
                      {:Authentication
                       (str "Bearer " (:token @state/session))}
                      {})

                    :params
                    (when (or (= operation :create)
                              (= operation :update)
                              (= operation :bulk)
                              (= operation :create-no-read))
                      params)

                    :handler
                    (if (= operation :read)
                      (fn [json]
                        (reset! state json)
                        (when handler (handler json)))
                      (fn [json]
                        (when handler (handler json))
                        (when-not (= operation :create-no-read)
                          (dispatch! :read nil nil nil))))

                    :error-handler
                    (fn [error]
                      (when error-handler
                        (error-handler error))
                      (cond
                        ; Informative error messages
                        (= (:status error) 401)
                        (when (:token @state/session)
                          (put! state/add-messages-chan
                                [:logged-out [:negative "Logged out"]])
                          (auth/sign-out!))

                        (= (:status error) 403)
                        (if (= (:status-text error) "Unable to charge credit card")
                          (put! state/add-messages-chan
                                [:bad-credit-card
                                 [:negative "Your card failed to be charged. Please try another."]])
                          (put! state/add-messages-chan
                                [:forbidden-action
                                 [:negative "You do not have permission to perform that action. Please reload the page."]]))

                        (= (:status error) 500)
                        (put! state/add-messages-chan
                              [:server-error
                               [:negative "An error occurred. Please reload the page."]])

                        (= (:status error) 404)
                        (put! state/add-messages-chan
                              [:not-found
                               [:negative "Your request was not found."]])

                        (= (:status error) 409)
                        (put! state/add-messages-chan
                              [:not-found
                               [:negative "A conflict has occurred. Please reload the page."]])

                        (= (:status error) 400)
                        (put! state/add-messages-chan
                              [:bad-request
                               [:negative "Bad request"]])

                        (= (:failure error) :timeout)
                        (put! state/add-messages-chan
                              [(keyword "elevent-client.api"
                                        (str uri "-timed-out"))
                               [:negative (str uri " timed out")]])

                        :else
                        (put! state/add-messages-chan
                              [(keyword "elevent-client.api" (gensym))
                               [:negative (str uri (js->clj error))]])))}]
       (let [check-id (fn [op] (if (contains? params element-id)
                                 (op (str uri "/" (params element-id)) options)
                                 (throw (str "Element doesn't contain key \""
                                             (prn-str element-id)
                                             "\": "
                                             (prn-str params)))))]
         (when @state/online?
           (case operation
             :create (POST uri options)
             :read   (GET  uri options)
             :update (PUT uri options)
             :delete (check-id DELETE)
             :create-no-read (POST uri options)
             (POST (str uri "/" (name operation)) options))))))
    ([operation params handler]
     (dispatch! operation params handler nil))))

;; Uses the endpoints macro to define endpoints, backing local storage, and
;; databases for the given endpoints.
(endpoints
  [attendees     (str config/https-url "/attendees")     :AttendeeId     true]
  [organizations (str config/https-url "/organizations") :OrganizationId false]
  [events        (str config/https-url "/events")        :EventId        false]
  [activities    (str config/https-url "/activities")    :ActivityId     false]
  [users         (str config/https-url "/users")         :UserId         true]
  [schedules     (str config/https-url "/schedules")     :ScheduleId     true]
  [memberships   (str config/https-url "/memberships")   :MembershipId   true]
  [groups        (str config/https-url "/groups")        :GroupId       true]
  [mandates      (str config/https-url "/mandates")      :MandateId      true]
  [permissions   (str config/https-url "/permissions")   :UserId         true])

;; If something is put on the state/api-refresh-chan, all of the endpoints are
;; refreshed.
(go-loop []
  (refresh!)
  (<! state/api-refresh-chan)
  (recur))

;; Listens to the state/auth-sign-up-chan and signs up the user put on the
;; channel.
(go-loop []
  (let [form (<! state/auth-sign-up-chan)]
    (users-endpoint :create-no-read form #(auth/sign-in! form))
    (recur)))

(def update-user-permissions-chan
  "Channel which prompts the user permissions to be updated. This is necessary
  because both the users and permissions databases need to be filled in in order
  to get the user permissons."
  (chan))

;; Fills in the :user map inthe  state/session atom and notifies
;; update-user-permissions-chan that it might want to update.
(add-watch users-db
           :users-update
           (fn [_ _ _ db]
             (when-let [email (get-in @state/session [:user :Email])]
               (swap! state/session
                      assoc
                      :user
                      (when-let [entity-id (->> email
                                                (d/q '[:find ?user-id
                                                       :in $ ?email
                                                       :where
                                                       [?user-id :Email ?email]]
                                                     db)
                                                ffirst)]
                        (into {} (d/entity db entity-id)))))
             (put! update-user-permissions-chan true)))

;; Notifies update-user-permissions-chan that it might want to update.
(add-watch permissions-db
           :permissions-update
           (fn [_ _ _ db]
             (put! update-user-permissions-chan true)))

;; Whenever a sigal comes over the update-user-permissions-chan, checks to see
;; if it can get the user permissions. If it can, it puts the on the
;; state/session atom as :permissions.
(go-loop []
  (<! update-user-permissions-chan)
  (when-let [user-id (get-in @state/session [:user :UserId])]
    (swap! state/session assoc
           :permissions
           (let [permissions (into {} (d/entity @permissions-db user-id))]
             (merge permissions
                    {:EventPermissions        (apply merge (map (fn [event] {(:EventId event) event})
                                                                (:EventPermissions permissions)))
                     :ActivityPermissions     (apply merge (map (fn [activity] {(:ActivityId activity) activity})
                                                                (:ActivityPermissions permissions)))
                     :OrganizationPermissions (apply merge (map (fn [org] {(:OrganizationId org) org})
                                                                (:OrganizationPermissions permissions)))}))))
  (recur))
