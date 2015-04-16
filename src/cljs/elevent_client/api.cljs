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

(defn api-call [op uri params handler & [error-handler]]
  (let [options {:format          :json
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

(defn endpoint [uri element-id state]
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
                      (if error-handler
                        (error-handler error)
                        (if (= (:status error) 401)
                          (when (:token @state/session)
                            (do
                              (put! state/add-messages-chan
                                    [::logged-out [:negative "Logged out"]])
                              (auth/sign-out!)))
                          (if (= (:failure error) :timeout)
                            (put! state/add-messages-chan
                                  [(keyword "elevent-client.api"
                                            (str uri "-timed-out"))
                                   [:negative (str uri " timed out")]])
                            (put! state/add-messages-chan
                                  [(keyword "elevent-client.api" (gensym))
                                   [:negative (str uri (js->clj error))]])))))}]
       (let [check-id (fn [op] (if (contains? params element-id)
                                 (op (str uri "/" (params element-id)) options)
                                 (throw (str "Element doesn't contain key \""
                                             (prn-str element-id)
                                             "\": "
                                             (prn-str params)))))]
         (case operation
           :create (POST uri options)
           :read   (GET  uri options)
           :update (PUT uri options)
           :delete (check-id DELETE)
           :create-no-read (POST uri options)
           (POST (str uri "/" (name operation)) options)))))
    ([operation params handler]
     (dispatch! operation params handler nil))))

(endpoints
  [attendees     (str config/https-url "/attendees")     :AttendeeId     true]
  [organizations (str config/https-url "/organizations") :OrganizationId false]
  [events        (str config/https-url "/events")        :EventId        false]
  [activities    (str config/https-url "/activities")    :ActivityId     false]
  [users         (str config/https-url "/users")         :UserId         true]
  [schedules     (str config/https-url "/schedules")     :ScheduleId     true]
  [permissions   (str config/https-url "/permissions")   :UserId         true])

(go-loop []
  (<! state/api-refresh-chan)
  (refresh!)
  (recur))

(go-loop []
  (let [form (<! state/auth-sign-up-chan)]
    (users-endpoint :create-no-read form #(auth/sign-in! form))
    (recur)))

(def update-user-permissions-chan (chan))

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

(add-watch permissions-db
           :permissions-update
           (fn [_ _ _ db]
             (put! update-user-permissions-chan true)))

(go-loop []
  (<! update-user-permissions-chan)
  (when-let [user-id (get-in @state/session [:user :UserId])]
    (swap! state/session assoc
           :permissions (into {} (d/entity @permissions-db user-id))))
  (recur))