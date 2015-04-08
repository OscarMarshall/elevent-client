(ns elevent-client.core
    (:require
      [clojure.set :as set]
      [clojure.string :as str]

      [goog.crypt.base64 :as b64]
      [goog.events :as events]
      [goog.history.EventType :as EventType]

      [cljsjs.react :as react]
      [reagent.core :as r :refer [atom]]

      [alandipert.storage-atom :refer [local-storage]]
      [ajax.core :refer [DELETE GET POST PUT]]
      [cljs-time.coerce :refer [from-string to-string from-long to-long from-date to-date]]
      [cljs-time.core :refer [after? at-midnight now plus minus hours]]
      [cljs-time.format :refer [formatter formatters unparse parse]]
      [datascript :as d]
      [garden.core :refer [css]]
      [secretary.core :as secretary :refer-macros [defroute]]
      [validateur.validation :refer [format-of
                                     inclusion-of
                                     numericality-of
                                     presence-of
                                     length-of
                                     valid?
                                     validation-set]])
    (:require-macros [elevent-client.core :refer [endpoints]])
    (:import goog.History))


(.initializeTouchEvents js/React true)


;; Date and Time formats
;; =============================================================================

(def date-format "MMMM d, yyyy")
(def time-format "h:mm a")
(def datetime-format (str time-format " " date-format))
(def input-date-time-format "MM/dd/YYYY, hh:mm a")
(def moment-date-time-format "MM/DD/YYYY, hh:mm a")
(def date-formatter (formatter date-format))
(def time-formatter (formatter time-format))
(def datetime-formatter (formatter datetime-format))
(def input-date-time-formatter (formatter input-date-time-format))

(def input-date-time-re #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d")



;; Location
;; =============================================================================

(defonce location (atom ""))


;; Session
;; =============================================================================
;;
;; An atom that stores state which should be persisted in LocalStorage

(def session (local-storage (atom {}) :session))


;; Messages
;; =============================================================================

(defonce messages (atom {}))
(defn add-message! [type message]
  (swap! messages assoc (keyword (gensym)) [type message]))


(def api-url "https://elevent.solutions:44300")


;; REST
;; =============================================================================

(declare sign-out!)

(defn endpoint [uri element-id state]
  (fn dispatch!
    ([operation params handler error-handler]
     (let [options {:format          :json

                    :response-format (when (= operation :read) :json)

                    :keywords?       true

                    :timeout         8000

                    :headers
                    (if (:token @session)
                      {:Authentication
                       (str "Bearer " (:token @session))}
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
                          (sign-out!)
                          (if (= (:failure error) :timeout)
                            (add-message! :negative (str uri " timed out"))
                            (add-message! :negative (str uri (js->clj error)))))))}]
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
  [attendees     (str api-url "/attendees")     :AttendeeId     true]
  [organizations (str api-url "/organizations") :OrganizationId false]
  [events        (str api-url "/events")        :EventId        false]
  [activities    (str api-url "/activities")    :ActivityId     false]
  [users         (str api-url "/users")         :UserId         true]
  [schedules     (str api-url "/schedules")     :ScheduleId     true])


;; Routes
;; =============================================================================

(declare home-page
         sign-in-page
         sign-up-page
         events-page
         events-explore-page
         events-owned-page
         event-page
         event-edit-page
         event-register-page
         event-activities-page
         event-activities-explore-page
         event-activity-page
         event-activity-edit-page
         event-schedule-page
         event-attendees-page
         event-attendee-page
         organizations-page
         organizations-explore-page
         organization-page
         organization-edit-page
         calendar-page
         statistics-page
         payments-page)

(defonce current-page (atom #'home-page))

(secretary/set-route-prefix! "#")

(defroute home-route
  "/" []
  (reset! current-page [#'home-page]))

(defroute sign-in-route "/sign-in" []
  (reset! current-page [#'sign-in-page]))

(defroute sign-up-route "/sign-up" []
  (reset! current-page [#'sign-up-page]))

(defroute events-explore-route
  "/events/explore" []
  (reset! current-page [#'events-explore-page]))

(defroute events-owned-route
  "/events/owned" []
  (reset! current-page [#'events-owned-page]))

(defroute events-route
  "/events" []
  (reset! current-page [#'events-page]))

(defroute event-add-route
  "/events/add" []
  (if (:token @session)
    (reset! current-page [#'event-edit-page])
    (location.replace (sign-in-route))))

(defroute event-route
  "/events/:EventId" [EventId]
  (reset! current-page [#'event-page (int EventId)]))

(defroute event-edit-route
  "/events/:EventId/edit" [EventId]
  (reset! current-page [#'event-edit-page (int EventId)]))

(defroute event-register-route
  "/events/:EventId/register" [EventId]
  (reset! current-page [#'event-register-page (int EventId)]))

(defroute event-activities-route
  "/events/:EventId/activities" [EventId]
  (reset! current-page [#'event-activities-page (int EventId)]))

(defroute event-activities-explore-route
  "/events/:EventId/activities/explore" [EventId]
  (reset! current-page [#'event-activities-explore-page (int EventId)]))

(defroute event-activity-add-route
  "/events/:EventId/activities/add" [EventId]
  (reset! current-page [#'event-activity-edit-page (int EventId)]))

(defroute event-schedule-route
  "/events/:EventId/schedule" [EventId]
  (reset! current-page [#'event-schedule-page (int EventId)]))

(defroute event-activity-route
  "/events/:EventId/activities/:ActivityId" [EventId ActivityId]
  (reset! current-page [#'event-activity-page (int EventId) (int ActivityId)]))

(defroute event-activity-edit-route
  "/events/:EventId/activities/:ActivityId/edit" [EventId ActivityId]
  (reset! current-page
          [#'event-activity-edit-page (int EventId) (int ActivityId)]))

(defroute event-attendees-route
  "/events/:EventId/attendees" [EventId]
  (reset! current-page [#'event-attendees-page (int EventId)]))

(defroute event-attendee-route
  "/events/:EventId/attendees/:AttendeeId" [EventId AttendeeId]
  (reset! current-page [#'event-attendee-page (int EventId) (int AttendeeId)]))

(defroute organizations-route
  "/organizations" []
  (reset! current-page [#'organizations-page]))

(defroute organizations-explore-route
  "/organizations/explore" []
  (reset! current-page [#'organizations-explore-page]))

(defroute organization-add-route
  "/organizations/add" []
  (reset! current-page [#'organization-edit-page]))

(defroute organization-route
  "/organizations/:OrganizationId" [OrganizationId]
  (reset! current-page [#'organization-page (int OrganizationId)]))

(defroute organization-edit-route
  "/organizations/:OrganizationId/edit" [OrganizationId]
  (reset! current-page [#'organization-edit-page (int OrganizationId)]))

(defroute calendar-route
  "/calendar" []
  (reset! current-page [#'calendar-page]))

(defroute statistics-route
  "/statistics" []
  (reset! current-page [#'statistics-page]))

(defroute payments-route
  "/payments" []
  (reset! current-page [#'payments-page]))

(def dispatch!
  (secretary/uri-dispatcher [home-route
                             sign-in-route
                             sign-up-route
                             events-explore-route
                             events-owned-route
                             events-route
                             event-add-route
                             event-route
                             event-edit-route
                             event-register-route
                             event-activities-explore-route
                             event-activities-route
                             event-activity-add-route
                             event-activity-route
                             event-activity-edit-route
                             event-schedule-route
                             event-attendees-route
                             event-attendee-route
                             organizations-route
                             organizations-explore-route
                             organization-add-route
                             organization-route
                             organization-edit-route
                             calendar-route
                             statistics-route
                             payments-route]))


;; User Account
;; =============================================================================

(defn sign-in! [form]
  (let [{:keys [Email Password]} form
        auth-string (b64/encodeString (str Email ":" Password))]
    (GET (str api-url "/token")
         {:format          :json
          :response-format :json
          :keywords?       true
          :headers         {:Authorization (str "Basic " auth-string)}
          :handler         (fn [response]
                             (swap! session assoc :token (:Token response))
                             (swap! session assoc-in [:user :Email] Email)
                             (refresh!)
                             (reset! messages {})
                             (add-message! :positive "Sign in succeeded")
                             (js/location.replace (events-route)))
          :error-handler   #(add-message! :negative "Sign in failed")})))

(defn sign-out! []
  (swap! session dissoc :token :user :stripe-token :payment-info)
  (refresh!)
  (set! js/window.location (home-route)))

(defn sign-up! [form]
  (users-endpoint :create-no-read form #(sign-in! form)))

(add-watch users-db
           :find-user
           (fn [_ _ _ db]
             (when-let [email (get-in @session [:user :Email])]
               (swap! session
                      assoc
                      :user
                      (when-let [entity-id (->> email
                                                (d/q '[:find ?user-id
                                                       :in $ ?email
                                                       :where [?user-id :Email ?email]]
                                                     db)
                                                ffirst)]
                        (into {} (d/entity db entity-id)))))))


;; Stylesheet
;; =============================================================================

(defn stylesheet []
  [:style (css [:.center {:display      "block"
                          :margin-left  "auto"
                          :margin-right "auto"}]
               [:.menu [:.logo.item {:padding-top    ".32em"
                                     :padding-bottom ".32em"}
                        [:img {:height "2em"}]]]
               [:.ui.vertical.segment:first-child {:padding-top 0}]
               [:.ui.vertical.segment:last-child {:padding-bottom 0}])])


;; Elements
;; =============================================================================

;; Modified version of Tim Gilbert's cljs-pikaday, Copyright (c) 2012 Tim Gilbert
;; See third-party/cljs-pikaday-license.txt
(defn date-selector
  "Return a date-selector reagent component. Takes a single map as its
  argument, with the following keys:
  date-atom: an atom or reaction bound to the date value represented by the picker.
  max-date-atom: atom representing the maximum date for the selector.
  min-date-atom: atom representing the minimum date for the selector.
  pikaday-attrs: a map of options to be passed to the Pikaday constructor.
  input-attrs: a map of options to be used as <input> tag attributes."
  [{:keys [date-atom max-date-atom min-date-atom pikaday-attrs input-attrs]}]
  (let [in #(if (from-string %)
              (unparse input-date-time-formatter (from-string %))
              %)
        out #(if (from-string %)
               (unparse (:date-hour-minute formatters) (from-string %))
               %)
        from-input-string #(let [in-date (try (parse input-date-time-formatter %) (catch :default _))]
                             (if in-date
                               (unparse (:date-hour-minute formatters) (parse input-date-time-formatter %))
                               %))
        normalize ; because of GMT
        #(to-date (plus (from-string %) (hours 6)))]
    (r/create-class
      {:component-did-mount
        (fn [this]
          (let [default-opts
                {:field (.getDOMNode this)
                 :defaultDate @date-atom
                 :setDefaultDate true
                 :onSelect #(when date-atom (reset! date-atom (out (to-string (minus (from-date %) (hours 6))))))}
                opts (clj->js (merge default-opts pikaday-attrs))
                instance (js/Pikaday. opts)]
            ; This code could probably be neater
            (when date-atom
              (add-watch date-atom :update-instance
                (fn [ke ref old new]
                  ; final parameter here causes pikaday to skip onSelect() callback
                  (when (from-string new)
                    (.setDate instance (normalize new) true)))))
            (when min-date-atom
              (add-watch min-date-atom :update-min-date
                (fn [key ref old new]
                  (when (from-string new)
                    (.setMinDate instance (normalize new))
                    ; If new min date is greater than selected date, reset actual date to min
                    (if (< @date-atom new)
                      (reset! date-atom new))))))
            (when max-date-atom
              (add-watch max-date-atom :update-max-date
                (fn [key ref old new]
                  (when (from-string new)
                    (.setMaxDate instance (normalize new))
                    ; If new max date is less than selected date, reset actual date to max
                    (if (> @date-atom new)
                      (reset! date-atom new))))))))
        :component-did-update
        (fn [this]
          (let [value (-> this
                          r/dom-node
                          js/jQuery
                          .val)]
            (when (from-string value)
              (-> this
                  r/dom-node
                  js/jQuery
                  (.val (in @date-atom))))))
        :reagent-render
        (fn [input-attrs]
          [:input (merge input-attrs {:on-change #(reset! date-atom
                                                          (from-input-string (.-value (.-target %))))})])})))

(defn input-atom
  ([type options select-options state in out]
   (let [in  (or in  identity)
         out (or out identity)]
     (r/create-class
       {:component-did-mount
        (fn [this]
          (when (= type :select)
            (-> this
                r/dom-node
                js/jQuery
                (.dropdown (clj->js {:onChange #(when % (reset! state
                                                                (out %)))})))))

        :component-did-update
        (fn [this]
          (when (= type :select)
            (-> this
                r/dom-node
                js/jQuery
                (.dropdown (clj->js {:onChange #(when % (reset! state
                                                                (out %)))})))))

        :reagent-render
        (fn render
          ([_ options select-options state _ _]
           (let [attributes (merge {:value     (in @state)
                                    :on-change #(reset! state
                                                        (out (.-value (.-target %))))}
                                   options)]
             (case type
               :textarea [:textarea attributes]
               :select [:div.ui.dropdown.selection
                        [:input (assoc attributes :type :hidden)]
                        [:div.text "None"]
                        [:i.dropdown.icon]
                        [:div.menu
                         (for [[name value] select-options]
                           ^{:key (or value 0)} [:div.item {:data-value value}
                                                 name])]]
               [:input (assoc attributes :type type)])))
          ([_ options state _ _]
           (render nil options nil state nil nil))
          ([_ options select-options state]
           (render nil options select-options state nil nil))
          ([_ options state]
           (render nil options nil state nil nil)))})))
  ([type options state in out]
   (input-atom type options nil state in out))
  ([type options select-options state]
   (input-atom type options select-options state nil nil))
  ([type options state]
   (input-atom type options nil state nil nil)))

(defn chart [config _]
  (let [data (atom nil)
        split-data
        (fn [data]
          (if (seq data)
            (if (= (:type (:chart config)) "bar")
              [(into [] (map second data)) (map first data)]
              [data nil])
            [nil nil]))]
    (r/create-class
      {:component-did-mount #(let [[series-data categories] (split-data @data)]
                               (.highcharts (js/jQuery (r/dom-node %))
                                            (clj->js (assoc-in
                                                       (assoc-in config
                                                                 [:series 0 :data] series-data)
                                                       [:xAxis :categories] categories))))
       :component-did-update #(let [[series-data categories] (split-data @data)
                                    chart (-> %
                                              r/dom-node
                                              js/jQuery
                                              .highcharts)]
                                (-> chart
                                    .-series
                                    first
                                    (.setData (clj->js series-data)))
                                (-> chart
                                    .-xAxis
                                    first
                                    (.setCategories (clj->js categories))))
       :reagent-render (fn [_ series]
                         (reset! data series)
                         [:div])})))

(defn calendar [options]
  (let [options (atom {})]
    (r/create-class
      {:component-did-mount #(.fullCalendar (js/jQuery (r/dom-node %))
                                            (clj->js @options))
       :component-did-update #(.fullCalendar (js/jQuery (r/dom-node %))
                                             (clj->js @options))
       :reagent-render (fn [x]
                         (reset! options x)
                         [:div])})))

(defn qr-code [options]
  (let [options (atom {})]
    (r/create-class
      {:component-did-mount #(.qrcode (js/jQuery (r/dom-node %))
                                      (clj->js @options))
       :component-did-update #(.qrcode (js/jQuery (r/dom-node %))
                                       (clj->js @options))
       :reagent-render (fn [x]
                         (reset! options x)
                         [:div])})))

; action must be a function that takes a callback for the API call as a param
(defn action-button [options text action & [alt-text]]
  (let [loading-text [:i.spinner.loading.icon]
        button-text  (atom text)]
    (fn [options text action & [alt-text alt-action]]
      [:div.ui.button
       (merge options
              {:on-click (fn []
                           (reset! button-text loading-text)
                           (if alt-text
                             (action #(reset! button-text alt-text))
                             (action #(reset! button-text text))))})
       @button-text])))


;; Compenents
;; =============================================================================

(defn navbar-component []
  [:nav.ui.fixed.menu
   [:a.logo.item {:href (home-route)}
    [:img {:src "images/logo-menu.png"}]]
   [:a.blue.item {:href (events-route)
                  :class (when (re-find (re-pattern (events-route)) @location)
                           "active")}
    [:i.ticket.icon]
    "Events"]
   [:a.blue.item {:href (organizations-route)
                  :class (when (re-find (re-pattern (organizations-route))
                                        @location)
                           "active")}
    [:i.building.icon]
    "Organizations"]
   [:a.blue.item {:href (calendar-route)
                  :class (when (re-find (re-pattern (calendar-route)) @location)
                           "active")}
    [:i.calendar.icon]
    "Calendar"]
   [:a.blue.item {:href (statistics-route)
                  :class (when (re-find (re-pattern (statistics-route))
                                        @location)
                           "active")}
    [:i.bar.chart.icon]
    "Statistics"]
   (if (:token @session)
     [:div.right.menu
      [:div.item
       [:i.user.icon]
       (:FirstName (:user @session))]
      [:a.blue.item {:on-click sign-out!}
       [:i.sign.out.icon]
       "Sign out"]]
     [:div.right.menu
      [:a.blue.item {:href (sign-in-route)
                     :class (when (re-find (re-pattern (sign-in-route))
                                           @location)
                              "active")}
       [:i.sign.in.icon]
       "Sign in"]
      [:a.blue.item {:href (sign-up-route)
                     :class (when (re-find (re-pattern (sign-up-route))
                                           @location)
                              "active")}
       [:i.add.user.icon]
       "Sign up"]])])

(defn breadcrumbs-component []
  (let [breadcrumber
        (fn breadcrumber [lambda]
          (fn
            ([results _] results)
            ([results env fragment]
             (let [[result env continuation] (lambda env fragment)]
               (partial (breadcrumber continuation)
                        (conj results result)
                        env)))))

        event-activity-breadcrumbs
        (fn [env _]
          [["Edit" (event-activity-edit-route env)] env nil])

        event-activities-breadcrumbs
        (fn [env fragment]
          (case fragment
            "explore" [["Explore" (event-activities-explore-route env)] env nil]
            "add" [["Add" (event-activity-add-route env)] env nil]
            (let [env (assoc env :ActivityId fragment)]
              [[(:Name (d/entity @activities-db (int fragment)))
                (event-activity-route env)]
               env
               event-activity-breadcrumbs])))

        event-attendees-breadcrumbs
        (fn [env fragment]
          (let [env
                (assoc env :AttendeeId fragment)

                entity
                (d/entity @users-db
                          (first (d/q '[:find [?user-id ...]
                                        :in $ ?attendee-id
                                        :where [?attendee-id :UserId ?user-id]]
                                      @attendees-db
                                      (int fragment))))]
            [[(str (:FirstName entity) " " (:LastName entity))
              env
              event-attendee-route]]))

        event-breadcrumbs
        (fn [env fragment]
          (case fragment
            "edit" [["Edit" (event-edit-route env)] env nil]
            "register" [["Register" (event-register-route env)] env nil]
            "activities" [["Activities" (event-activities-route env)]
                          env
                          event-activities-breadcrumbs]
            "attendees" [["Attendees" (event-attendees-route env)]
                         env
                         event-attendees-breadcrumbs]
            "schedule" [["Schedule" (event-schedule-route env)] env nil]))

        events-breadcrumbs
        (fn [env fragment]
          (case fragment
            "explore" [["Explore" (events-explore-route)] env nil]
            "owned" [["Owned" (events-owned-route)] env nil]
            "add" [["Add" (event-add-route)] env nil]
            (let [env (assoc env :EventId fragment)]
              [[(:Name (d/entity @events-db (int fragment))) (event-route env)]
               env
               event-breadcrumbs])))

        organization-breadcrumbs
        (fn [env _]
          [["Edit" (organization-edit-route env)] env nil])

        organizations-breadcrumbs
        (fn [env fragment]
          (case fragment
            "explore" [["Explore" (organizations-explore-route)] env nil]
            "add" [["Add" (organization-add-route)] env nil]
            (let [env (assoc env :OrganizationId fragment)]
              [[(:Name (d/entity @organizations-db (int fragment)))
                (organization-route env)]
               env
               organization-breadcrumbs])))

        top-breadcrumbs
        (fn [env fragment]
          (case fragment
            "events" [["Events" (events-route)] env events-breadcrumbs]
            "organizations" [["Organizations" (organization-route)]
                             env
                             organizations-breadcrumbs]
            "calendar" [["Calendar" (calendar-route)] env nil]
            "statistics" [["Statistics" (statistics-route)] env nil]
            "payments" [["Payments" (payments-route)] env nil]
            "sign-in" [["Sign in" (sign-in-route)] env nil]
            "sign-up" [["Sign up" (sign-up-route)] env nil]))

        get-breadcrumbs
        (fn
          ([] [["Home" (home-route)]])
          ([fragment]
           ((breadcrumber
              (fn [env _] [["Home" (home-route)] env top-breadcrumbs]))
            []
            {}
            fragment)))

        breadcrumbs
        ((reduce #(%1 %2) get-breadcrumbs (str/split @location #"/")))]
    [:div.sixteen.wide.column
     [:div.ui.breadcrumb
      (map-indexed (fn [index [name hash]]
                     (condp = index
                       0
                       (if (= (count breadcrumbs) 1)
                         ^{:key (str "only-" hash)}
                         [:div.active.section name]

                         ^{:key (str "first-" hash)}
                         [:a.section {:href hash} name])

                       (dec (count breadcrumbs))
                       ^{:key (str "last-" hash)}
                       [:span
                        [:div.divider "/"]
                        [:div.active.section name]]

                       ^{:key hash}
                       [:span
                        [:div.divider "/"]
                        [:a.section {:href hash} name]]))
                   breadcrumbs)]]))

(defn messages-component []
  (when-let [messages* (seq @messages)]
    [:div.sixteen.wide.column
     (for [[key [type message]] messages*]
       ^{:key key}
       [:div.ui.message {:class type}
        [:i.close.icon {:on-click #(swap! messages dissoc key)}]
        message])]))

(defn payments-component []
  (let [form (atom {})
        validator (validation-set (presence-of :number)
                                  (presence-of :cvc)
                                  (presence-of :exp-date)
                                  (format-of   :number   :format #"^[0-9]{16}$")
                                  (format-of   :cvc      :format #"^[0-9]{3}$")
                                  (format-of   :exp-date :format #"^[0-1][0-9]/20[1-9][0-9]$"))
        editing? (atom false)
        stripe-error (atom nil)
        button-loading? (atom false)]
    (fn []
      (Stripe.setPublishableKey "pk_test_7ntI7D72loXtuO2F15gV0nR0")
      (let [{:keys [number cvc exp-date]} @form
            errors (validator @form)

            response-handler
            (fn [status response]
              (reset! button-loading? false)
              (prn response)
              (if response.error
                (do
                  (reset! stripe-error response.error.message))
                (let [[month year] (clojure.string/split (:exp-date @form) #"/" 2)]
                  (reset! stripe-error nil)
                  (reset! editing? false)
                  (swap! session assoc :stripe-token response.id)
                  (swap! session assoc :payment-info (dissoc (assoc @form
                                                               :exp-month (int month)
                                                               :exp-year (int year))
                                                             :exp-date)))))

            create-token
            (fn [e form]
              (when (empty? errors)
                (let [[month year] (clojure.string/split (:exp-date form) #"/" 2)]
                  (reset! button-loading? true)
                  (prn (dissoc (assoc form
                                 :exp-month (int month)
                                 :exp-year (int year))
                               :exp-date))
                  (.preventDefault e)
                  (.stopPropagation e)
                  (Stripe.card.createToken
                    (clj->js (dissoc (assoc form
                                       :exp-month (int month)
                                       :exp-year (int year))
                                     :exp-date))
                    response-handler))))]
        #_(when (:payment-info @session)
          (swap! form assoc
                 :number   (str "************" (subs (get-in @session [:payment-info :number]) 12))
                 :cvc      (get-in @session [:payment-info :cvc])
                 :exp-date (str (get-in @session [:payment-info :exp-month])
                                "/"
                                (get-in @session [:payment-info :exp-year]))))
        (if (or (nil? (:payment-info @session))
                @editing?)
          [:div.ui.vertical.segment
           [:h2.ui.dividing.header
            "Payment Info"]
           [:div.two.fields
            [:div.required.field {:class (when (and number (:number errors))
                                           "error")
                                  :on-change #(swap! session dissoc :payment-info)}
             [:label "Card Number"]
             [input-atom :text {}
              (r/wrap number swap! form assoc :number)]]
            [:div.required.field {:class (when (and cvc (:cvc errors))
                                           "error")
                                  :on-change #(swap! session dissoc :payment-info)}
             [:label "CVC"]
             [:div.two.fields
              [:div.field
               [input-atom :password {}
                (r/wrap cvc swap! form assoc :cvc)]]
              [:div.field]]]]
           [:div.two.fields
            [:div.required.field {:class (when (and exp-date (:exp-date errors))
                                           "error")
                                  :on-change #(swap! session dissoc :payment-info)}
             [:label "Expiration Date"]
             [input-atom :text {:placeholder "MM/YYYY"}
              (r/wrap exp-date swap! form assoc :exp-date)]]
            [:div.field]]
           [:button.ui.primary.button
            {:type :submit
             :class (when (seq errors) "disabled")
             :on-click #(create-token % @form)}
            (if @button-loading?
              [:i.spinner.loading.icon]
              "Confirm")]
           [:span.ui.red.compact.message
            {:class (when (nil? @stripe-error) "hidden")}
            @stripe-error]]
          [:div.inline.fields
           [:div.field (str "Charging card ending in "
                            (subs (get-in @session [:payment-info :number]) 12)
                            ".")]
           [:a.field {:on-click #(swap! session dissoc :payment-info)
                      :style {:cursor "pointer"}}
            "Charge different card"
            [:i.right.chevron.icon]]])))))


;; Helpers
;; =============================================================================

(defn renew-stripe-token! [callback]
  (let [response-handler
        (fn [status response]
          (when (not response.error)
            (do
              (swap! session assoc :stripe-token response.id)
              (callback))))]
    (Stripe.card.createToken
      (clj->js (:payment-info @session))
      response-handler)))

(defn activity-table-component [event-id]
  (let [activities (doall (map #(into {} (d/entity @activities-db %))
                               (d/q '[:find [?activity-id ...]
                                      :in $ ?event-id
                                      :where [?activity-id :EventId ?event-id]]
                                    @activities-db
                                    event-id)))]
    [:table.ui.table
     [:thead
      [:tr
       [:th "Start Time"]
       [:th "End Time"]
       [:th "Activity"]
       [:th "Location"]
       [:th "Actions"]]]
     [:tbody
      (for [activity activities]
        ^{:key (:ActivityId activity)}
        [:tr
         [:td (let [start (from-string (:StartTime activity))]
                (unparse datetime-formatter start))]
         [:td (let [end   (from-string (:EndTime   activity))]
                (unparse datetime-formatter end))]
         [:td (:Name activity)]
         [:td (:Location activity)]
         [:td [:a.ui.tiny.labeled.icon.button
               {:href (event-activity-edit-route activity)}
               [:i.edit.icon]
               "Edit"]]])]]))


;; Pages
;; =============================================================================

(defn home-page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    [:h2.ui.dividing.header "Welcome to Elevent Solutions"]
    [:p
     "Elevent is a large-event management application that provides a smooth "
     "experience for all stages of the event lifecycle. From planning "
     "activities for your next corporate convention, to handling check-in at "
     "your academic conference, Elevent has you covered. Using our "
     "application, event organizers can easily plan their event and set up "
     "smaller activities that occur during the event. Organizers can also "
     "invite others, manage the permissions of those attending, and view "
     "statistics about their events. Attendees are able to use Elevent to "
     "register and pay for events, and plan out the activities they will "
     "attend. Elevent Solutions provides the best possible experience for "
     "event organizers, attendees, and everyone in between."]]])

(defn sign-in-page []
  (let [form (atom {})
        validator (validation-set (format-of :Email :format #"@")
                                  (presence-of :Password))]
    (fn []
      (let [{:keys [Email Password]} @form
            errors                   (validator @form)]
        [:div.eight.wide.centered.column
         [:div.ui.segment
          [:h1.ui.dividing.header "Sign In"]
          [:form.ui.form {:on-submit (fn [e]
                                       (.preventDefault e)
                                       (when (empty? errors)
                                         (sign-in! @form)))}
           [:div.two.fields
            [:div.required.field {:class (when (and Email (:Email errors))
                                           "error")}
             [:label "Email"]
             [:div.ui.icon.input
              [input-atom :email {}
               (r/wrap Email swap! form assoc :Email)]
              [:i.mail.icon]]]
            [:div.required.field {:class (when (and Password (:Password errors))
                                           "error")}
             [:label "Password"]
             [:div.ui.icon.input
              [input-atom :password {}
               (r/wrap Password swap! form assoc :Password)]
              [:i.lock.icon]]]]
           [:button.ui.primary.button {:type :submit
                                       :class (when (seq errors) "disabled")}
            "Sign in"]]]]))))

(defn sign-up-page []
  (let [form (atom {})]
    (fn []
      (let [{:keys [Email Password PasswordConfirm FirstName LastName]} @form
            validator (validation-set (format-of :Email :format #"@")
                                      (presence-of :Password)
                                      (inclusion-of :PasswordConfirm
                                                    :in #{(:Password @form)})
                                      (presence-of :FirstName)
                                      (presence-of :LastName)
                                      (length-of   :Password :within (range 8 100)))
            errors                   (validator @form)]
        [:div.eight.wide.centered.column
         [:div.ui.segment
          [:h1.ui.dividing.header "Sign Up"]
          [:form.ui.form {:on-submit (fn [e]
                                       (.preventDefault e)
                                       (when (empty? errors)
                                         (sign-up! @form)))}
           [:div.required.field {:class (when (and Email (:Email errors))
                                          "error")}
            [:label "Email"]
            [:div.ui.icon.input
             [input-atom :email {}
              (r/wrap Email swap! form assoc :Email)]
             [:i.mail.icon]]]
           [:div.two.fields
            [:div.required.field {:class (when (and Password (:Password errors))
                                           "error")}
             [:label "Password"]
             [:div.ui.icon.input
              [input-atom :password {}
               (r/wrap Password swap! form assoc :Password)]
              [:i.lock.icon]]]
            [:div.required.field {:class (when (and PasswordConfirm
                                                    (:PasswordConfirm errors))
                                           "error")}
             [:label "Confirm Password"]
             [:div.ui.input
              [input-atom :password {}
               (r/wrap PasswordConfirm swap! form assoc :PasswordConfirm)]]]]
           [:div.two.fields
            [:div.required.field {:class (when (and FirstName
                                                    (:FirstName errors))
                                           "error")}
             [:label "First Name"]
             [:div.ui.input
              [input-atom :text {}
               (r/wrap FirstName swap! form assoc :FirstName)]]]
            [:div.required.field {:class (when (and LastName
                                                    (:LastName errors))
                                           "error")}
             [:label "Last Name"]
             [:div.ui.input
              [input-atom :text {}
               (r/wrap LastName swap! form assoc :LastName)]]]]
           [:button.ui.primary.button {:type :submit
                                       :class (when (seq errors) "disabled")}
            "Sign up"]]]]))))

(defn events-page []
  (let [leave-event (fn [attendee-id]
                      ; TODO: this doesn't delete schedules
                      (attendees-endpoint :delete
                                          (d/entity @attendees-db attendee-id)
                                          nil))]
    (fn []
      (if (:token @session)
        (let [attending-events
              (doall (map (fn [[event-id attendee-id]]
                            (merge
                              (into {} (d/entity @events-db event-id))
                              {:AttendeeId attendee-id}))
                          (d/q '[:find ?event-id ?attendee-id
                                 :in $ ?user-id
                                 :where
                                 [?attendee-id :UserId  ?user-id]
                                 [?attendee-id :EventId ?event-id]]
                               @attendees-db
                               (get-in @session [:user :UserId]))))]
          [:div.sixteen.wide.column
           [:div.ui.top.attached.tabular.menu
            [:a.active.item {:href (events-route)}
             "Events"]
            [:a.item {:href (events-explore-route)}
             "Explore"]
            [:a.item {:href (events-owned-route)}
             "Owned"]
            [:a.item {:href (event-add-route)}
             "Add"]]
           [:div.ui.bottom.attached.segment
            [:div
             [:div.ui.vertical.segment
              [:h1.ui.header "Events You're Attending"]]
             [:div.ui.vertical.segment
              (if (seq attending-events)
                [:div.ui.divided.items
                 (for [event attending-events]
                   ^{:key (:EventId event)}
                   [:div.item
                    [:div.content
                     [:a.header {:href (event-route event)}
                      (:Name event)]
                     [:div.meta
                      [:strong "Date:"]
                      (let [start (from-string (:StartDate event))
                            end   (from-string (:EndDate   event))]
                        (str (unparse datetime-formatter start)
                             (when (after? end start)
                               (str " to " (unparse datetime-formatter end)))))]
                     [:div.meta
                      [:strong "Venue:"]
                      (:Venue event)]
                     [:div.description
                      (:Description event)]
                     [:div.extra
                      [:a.ui.right.floated.small.button {:href (event-schedule-route event)}
                       "Your activities"
                       [:i.right.chevron.icon]]
                      [:a.ui.right.floated.small.button
                       {:on-click #(leave-event (:AttendeeId event))}
                       [:i.red.remove.icon]
                       "Leave event"]]]])]
                [:p "You aren't attending any events."])]]
            [:div.ui.dimmer {:class (when (empty? @events) "active")}
             [:div.ui.loader]]]])
        (do
          (js/location.replace (events-explore-route))
          [:div])))))

(defn events-explore-page []
  (let [unattending-events
        (map (partial d/entity @events-db)
             (set/difference (into #{} (d/q '[:find [?event-id ...]
                                              :where [?event-id]]
                                            @events-db))
                             (into #{} (d/q '[:find [?event-id ...]
                                              :in $ ?user-id
                                              :where
                                              [?attendee-id :EventId ?event-id]
                                              [?attendee-id :UserId ?user-id]]
                                            @attendees-db
                                            (get-in @session
                                                    [:user :UserId])))))]
    [:div.sixteen.wide.column
     [:div.ui.top.attached.tabular.menu
      [:a.item {:href (events-route)}
       "Events"]
      [:a.active.item {:href (events-explore-route)}
       "Explore"]
      [:a.item {:href (events-owned-route)}
       "Owned"]
      [:a.item {:href (event-add-route)}
       "Add"]]
     [:div.ui.bottom.attached.segment
      [:div
       [:div.ui.vertical.segment
        [:h1.ui.header "Explore Events"]]
       [:div.ui.vertical.segment
        [:div.ui.divided.items
         (for [event (sort-by :StartDate unattending-events)]
           ^{:key (:EventId event)}
           [:div.item
            [:div.content
             [:a.header {:href (event-route event)}
              (:Name event)]
             [:div.meta
              [:strong "Date:"]
              (let [start (from-string (:StartDate event))
                    end   (from-string (:EndDate   event))]
                (str (unparse datetime-formatter start)
                     (when (after? end start)
                       (str " to " (unparse datetime-formatter end)))))]
             [:div.meta
              [:strong "Venue:"]
              (:Venue event)]
             [:div.description
              (:Description event)]
             [:div.extra
              [:a.ui.right.floated.button
               {:href (event-register-route event)}
               "Register"
               [:i.right.chevron.icon]]]]])]]]
      [:div.ui.dimmer {:class (when-not @events "active")}
       [:div.ui.loader]]]]))

(defn events-owned-page []
  (let [created-events (doall (map #(into {} (d/entity @events-db %))
                                   (d/q '[:find [?event-id ...]
                                          :in $ ?user-id
                                          :where
                                          [?event-id :CreatorId  ?user-id]]
                                        @events-db
                                        (get-in @session [:user :UserId]))))]
    [:div.sixteen.wide.column
     [:div.ui.top.attached.tabular.menu
      [:a.item {:href (events-route)}
       "Events"]
      [:a.item {:href (events-explore-route)}
       "Explore"]
      [:a.active.item {:href (events-owned-route)}
       "Owned"]
      [:a.item {:href (event-add-route)}
       "Add"]]
     [:div.ui.bottom.attached.segment
      [:div
       [:div.ui.vertical.segment
        [:h1.ui.header "Events You Own"]]
       [:div.ui.vertical.segment
        (if (seq created-events)
          [:div.ui.divided.items
           (for [event created-events]
             ^{:key (:EventId event)}
             [:div.item
              [:div.content
               [:a.header {:href (event-route event)}
                (:Name event)]
               [:div.meta
                [:strong "Date:"]
                (let [start (from-string (:StartDate event))
                      end   (from-string (:EndDate   event))]
                  (str (unparse datetime-formatter start)
                       (when (after? end start)
                         (str " to " (unparse datetime-formatter end)))))]
               [:div.meta
                [:strong "Venue:"]
                (:Venue event)]
               [:div.description
                (:Description event)]
               [:div.extra
                [:a.ui.right.floated.small.button
                 {:href (event-edit-route event)}
                 "Edit"
                 [:i.right.chevron.icon]]]]])]
          [:p "You don't own any events."])]]
      [:div.ui.dimmer {:class (when (empty? @events) "active")}
       [:div.ui.loader]]]]))

(defn event-page [event-id]
  (let [event (into {} (d/entity @events-db event-id))

        activities (map #(d/entity @activities-db %)
                        (d/q '[:find [?e ...]
                               :in $ ?event-id
                               :where
                               [?e :EventId ?event-id]]
                             @activities-db
                             event-id))
        attendees (doall (take 10
                               (map (fn [[user-id attendee-id]]
                                      (merge
                                        (into
                                          {}
                                          (d/entity
                                            @users-db
                                            user-id))
                                        (into
                                          {}
                                          (d/entity
                                            @attendees-db
                                            attendee-id))))
                                    (d/q '[:find ?e ?a
                                           :in $ ?event-id
                                           :where
                                           [?a :EventId ?event-id]
                                           [?a :UserId ?e]]
                                         @attendees-db
                                         event-id))))]
    (when (seq event)
      [:div.sixteen.wide.column
       [:div.ui.segment
        [:div.ui.vertical.segment
         [:h1.ui.dividing.header
          (:Name event)]
         [:div.ui.right.floated.small.labeled.icon.button
          [:i.edit.icon]
          "Edit"]
         [:div
          [:b "Date: "]
          (when (and (:StartDate event)
                     (:EndDate event))
            (let [start (from-string (:StartDate event))
                  end   (from-string (:EndDate   event))]
              (str (unparse datetime-formatter start)
                   (when (after? end start)
                     (str " to "
                          (unparse datetime-formatter end))))))]
         [:div
          [:b "Venue: "] (:Venue event)]
         [:p (:Description event)]]
        (when-let [attendee-id (first (d/q '[:find [?attendee-id ...]
                                             :in $ ?event-id ?user-id
                                             :where
                                             [?attendee-id :EventId ?event-id]
                                             [?attendee-id :UserId ?user-id]]
                                           @attendees-db
                                           event-id
                                           (:UserId (:user @session))))]
          [:div.ui.vertical.segment
           [:h2 "QR-Code"]
           [qr-code
            {:text (event-attendee-route (into {} (d/entity @attendees-db
                                                            attendee-id)))}]])
        [:div.ui.vertical.segment
         [:h2.ui.header
          "Activities"]
         [activity-table-component event-id]]
        [:div.ui.vertical.segment
         [:h2.ui.header
          "Attendees"
          [:a.ui.right.floated.small.button
           {:href (event-attendees-route event)}
           "View"]]
         [:table.ui.table
          [:thead
           [:tr
            [:th "Name"]
            [:th]]]
          [:tbody
           (for [attendee attendees]
             ^{:key (:AttendeeId attendee)}
             [:tr
              [:td (str (:FirstName attendee) " " (:LastName attendee))]
              [:td [:a.ui.right.floated.small.labeled.button
                    {:href (event-attendee-route {:EventId (:EventId event)
                                                  :AttendeeId (:AttendeeId attendee)})
                     :class (when (:CheckinTime attendee) :green)}
                    (if (:CheckinTime attendee)
                      "Checked in"
                      "Check in")]]])]
          [:tfoot
           [:tr
            [:th {:colSpan "4"}
             [:div.ui.right.floated.small.labeled.icon.button
              [:i.edit.icon]
              "Edit"]]]]]]]])))

(defn event-edit-page []
  (let [form (atom {})
        validator (validation-set (presence-of :Name)
                                  (presence-of :OrganizationId)
                                  (presence-of :Venue)
                                  (presence-of :StartDate)
                                  (presence-of :EndDate)
                                  (format-of   :TicketPrice :format #"^\d*\.\d\d$"
                                               :allow-nil true
                                               :allow-blank true)
                                  (format-of   :StartDate :format #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d")
                                  (format-of   :EndDate   :format #"\d\d\d\d-\d\d-\d\dT\d\d:\d\d"))
        clone-id (atom 0)]
    (add-watch clone-id :clone
               (fn [_ _ _ id]
                 (when-not (zero? (int id))
                   (let [clone-event (->> id
                                          int
                                          (d/entity @events-db)
                                          seq
                                          (into {}))]
                     (reset! form (dissoc
                                    (assoc
                                      clone-event
                                      :TicketPrice
                                      (if (> (:TicketPrice clone-event) 0)
                                        (goog.string.format "%.2f" (:TicketPrice clone-event))
                                        ""))
                                    :EventId))))))
    (fn []
      (let [{:keys [Name OrganizationId Venue StartDate EndDate RequiresPayment TicketPrice Description]}
            @form

            errors
            (validator @form)

            clonable-events
            (cons ["None" 0]
                  (d/q '[:find ?name ?id
                         :where [?id :Name ?name]]
                       @events-db))

            associated-organizations
            (cons ["None" 0]
                  (d/q '[:find ?name ?id
                         :where [?id :Name ?name]]
                       @organizations-db))

            create-event
            (fn [form]
              (fn [callback]
                (when (empty? errors)
                  (events-endpoint :create
                                   form
                                   #(do
                                      (callback)
                                      (js/location.replace (events-explore-route)))))))]
        [:div.sixteen.wide.column
         [:div.ui.top.attached.tabular.menu
          [:a.item {:href (events-route)}
           "Events"]
          [:a.item {:href (events-explore-route)}
           "Explore"]
          [:a.item {:href (events-owned-route)}
           "Owned"]
          [:a.active.item {:href (event-add-route)}
           "Add"]]
         [:div.ui.bottom.attached.segment
          [:form.ui.form
           [:div.ui.vertical.segment
            [:h2.ui.dividing.header "Add an Event"]
            [:div.two.fields
             [:div.required.field {:class (when (and Name (:Name errors))
                                            "error")}
              [:label "Name"]
              [input-atom :text {} (r/wrap Name swap! form assoc :Name)]]
             [:div.field
              [:label "Clone From"]
              [input-atom :select {} clonable-events clone-id]]]
            [:div.two.fields
             [:div.required.field
              [:label "Organization"]
              [input-atom :select {} associated-organizations
               (r/wrap OrganizationId swap! form assoc :OrganizationId)]]
             [:div.required.field {:class (when (and Venue (:Venue errors))
                                            "error")}
              [:div.required.field
               [:label "Venue"]
               [input-atom :text {} (r/wrap Venue swap! form assoc :Venue)]]]]
            (let [start-date (r/wrap StartDate swap! form assoc :StartDate)
                  end-date   (r/wrap EndDate swap! form assoc :EndDate)]
              [:div.two.fields
               [:div.required.field {:class (when (and StartDate (:StartDate errors))
                                              "error")}
                [:label "Start Date"]
                [date-selector {:date-atom start-date
                                :max-date-atom end-date
                                :pikaday-attrs {:minDate (to-date (plus (now) (hours 6)))}}]]
               [:div.required.field {:class (when (and EndDate (:EndDate errors))
                                              "error")}
                [:label "End Date"]
                [date-selector {:date-atom end-date
                                :min-date-atom start-date}]]])
            [:div.field
             [:div.four.wide.field {:class (when (and TicketPrice (:TicketPrice errors))
                                   "error")}
              [:label "Ticket Price"]
              [:div.ui.labeled.input
               [:div.ui.label "$"]
               [input-atom :text {}
                (r/wrap TicketPrice swap! form assoc :TicketPrice)]]]]
            [:div.field
             [:div.field
              ; TODO: someday make UI checkboxes work
              #_[:div.field
              [(with-meta identity
                          {:component-did-mount #(.checkbox (js/$ ".ui.checkbox"))})
               [:div#requires-payment.ui.checkbox
                [:input {:type "checkbox"}]
                [:label "Ticket required"]]]]
               [:label
                [:input#requires-payment {:type "checkbox"
                                          :on-change #(swap! form assoc :RequiresPayment
                                                             (if (nil? (:RequiresPayment @form))
                                                               true
                                                               (not (:RequiresPayment @form))))}]
                " Ticket required"]]]
            [:div.field
             [:label "Description"]
             [input-atom :textarea {}
              (r/wrap Description swap! form assoc :Description)]]
            [action-button
             {:class (str "primary" (when (seq errors) " disabled"))
              :type :submit}
             "Add"
             (create-event @form)]]]]]))))

(defn event-activity-edit-page [event-id & [activity-id]]
  (let [form (atom {:EventId event-id :EnrollmentCap ""})
        reset-form!
        (reset! form {:EventId event-id :EnrollmentCap ""})
        validator (validation-set (presence-of :Name)
                                  (presence-of :StartTime)
                                  (presence-of :EndTime)
                                  (format-of :EnrollmentCap :format #"^\d*$"
                                             :allow-blank true
                                             :allow-nil true))]
    (when activity-id
      (if-let [activity (seq (d/entity @activities-db activity-id))]
        (reset! form (let [activity (into {} activity)]
                       (assoc activity
                         :EnrollmentCap (str (:EnrollmentCap activity)))))
        (add-watch activities-db
                   :activity-edit
                   (fn [_ _ _ _]
                     (reset! form
                             (let [activity (into {} (d/entity @activities-db
                                                               activity-id))]
                               (assoc activity
                                 :EnrollmentCap
                                 (str (:EnrollmentCap activity)))))
                     (remove-watch activities-db :activity-edit)))))
    (fn [event-id]
      (let [{:keys [Name Location EnrollmentCap StartTime EndTime Description]}
            @form

            errors
            (validator @form)

            event
            (into {} (d/entity @events-db event-id))

            activities
            (doall (map #(d/entity @activities-db %)
                        (d/q '[:find [?e ...]
                               :in $ ?event-id
                               :where
                               [?e :EventId ?event-id]]
                             @activities-db)))

            create-activity
            (fn [form]
              (fn [callback]
                (when (empty? errors)
                  (activities-endpoint :create
                                       (let [start-time (:StartTime form)
                                             end-time   (:EndTime form)]
                                         (assoc form
                                           :StartTime (unparse (:date-hour-minute-second formatters)
                                                               (from-string start-time))
                                           :EndTime (unparse (:date-hour-minute-second formatters)
                                                             (from-string end-time))))
                                       #(do
                                          (callback)
                                          (reset-form!))))))]
        (when (seq event)
          [:div.sixteen.wide.column
           [:div.ui.segment
            [:div.ui.vertical.segment
             [:h2.ui.header
              (:Name event)]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              (if activity-id "Edit" "Add") " activity"]
             [:form.ui.form {:on-submit
                             (fn [e]
                               (when (empty? errors)
                                 (.preventDefault e)
                                 (activities-endpoint (if activity-id
                                                        :update
                                                        :create)
                                                      @form
                                                      #(reset-form!))))}
              [:div.one.field
               [:div.required.field {:class (when (and Name (:Name errors))
                                              "error")}
                [:label "Name"]
                [input-atom :text {} (r/wrap Name swap! form assoc :Name)]]]
              [:div.two.fields
               [:div.field
                [:label "Location"]
                [input-atom :text {} (r/wrap Location swap! form assoc :Location)]]
               [:div.field {:class (when (and EnrollmentCap (:EnrollmentCap errors))
                                     "error")}
                [:label "Enrollment Cap"]
                [input-atom :text {} (r/wrap EnrollmentCap swap! form assoc :EnrollmentCap)]]]
              [:div.two.fields =
               [:div.required.field {:class (when (and StartTime (:StartTime errors))
                                              "error")}
                [:label "Start Time"]
                [input-atom :datetime-local {}
                 (r/wrap StartTime swap! form assoc :StartTime)
                 #(or % (unparse (:date-hour-minute formatters) (now))) ; todo: date inputs clear on input
                 #(unparse (:date-hour-minute formatters) (from-string %))]]
               [:div.required.field {:class (when (and EndTime (:EndTime errors))
                                              "error")}
                [:label "End Time"]
                [input-atom :datetime-local {}
                 (r/wrap EndTime swap! form assoc :EndTime)
                 #(or % (unparse (:date-hour-minute formatters) (now)))
                 #(unparse (:date-hour-minute formatters) (from-string %))]]]
              [:div.field
               [:label "Description"]
               [input-atom :textarea {}
                (r/wrap Description swap! form assoc :Description)]]
              [action-button
               {:class (str "primary" (when (seq errors) " disabled"))
                :type :submit}
               (if activity-id "Edit" "Add")
               (create-activity @form)]]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              "Activities"]
             [activity-table-component event-id]]]])))))

(defn event-attendees-page [event-id]
  (let [form (atom {:EventId event-id})]
    (fn [event-id]
      (let
        [{:keys [email-filter last-name-filter first-name-filter]}
         @form

         event (into {} (d/entity @events-db event-id))

         attendees
         (sort-by (juxt :LastName :FirstName)
                  (map (fn [[user-id attendee-id]]
                         (merge (into {} (d/entity @users-db
                                                   user-id))
                                (into {} (d/entity @attendees-db
                                                   attendee-id))))
                       (d/q '[:find ?e ?a
                              :in $ ?event-id
                              :where
                              [?a :EventId ?event-id]
                              [?a :UserId ?e]]
                            @attendees-db
                            event-id)))

         create-filter (fn [[keywords attribute]]
                         #(or (empty? keywords)
                              (re-find
                                (re-pattern (str/lower-case keywords))
                                (str/lower-case (or (% attribute) "")))))

         passes-filters?
         #(every? identity ((apply juxt (map create-filter
                                             [[email-filter       :Email]
                                              [last-name-filter   :LastName]
                                              [first-name-filter  :FirstName]])) %))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:h2.ui.header
           "Attendees"]
          [:table.ui.table
           [:thead
            [:tr
             [:th "Email"]
             [:th "Last Name"]
             [:th "First Name"]
             [:th]]]
           [:tbody
            [:tr.ui.form
             [:td
              [input-atom :text {} (r/wrap email-filter swap! form assoc :email-filter)]]
             [:td
              [input-atom :text {} (r/wrap last-name-filter swap! form assoc :last-name-filter)]]
             [:td
              [input-atom :text {} (r/wrap first-name-filter swap! form assoc :first-name-filter)]]
             [:td {:style {:text-align :center}}
              (str (reduce #(if (:CheckinTime %2)
                              (inc %1)
                              %1)
                           0
                           attendees)
                   "/"
                   (count attendees))]]
            (for [attendee attendees]
              ^{:key (:AttendeeId attendee)}
              [:tr {:style {:display (when-not (passes-filters? attendee)
                                       :none)}}
               [:td (:Email      attendee)]
               [:td (:LastName   attendee)]
               [:td (:FirstName  attendee)]
               [:td {:noWrap true}
                [:a.ui.right.floated.small.labeled.button
                 {:class (when (:CheckinTime attendee) "green")
                  :style {:width "100%"}
                  :href (event-attendee-route {:EventId event-id
                                               :AttendeeId (:AttendeeId attendee)})}
                 (if (:CheckinTime attendee)
                   "Checked in"
                   "Check in")]]])]]]]))))

(defn event-attendee-page [event-id attendee-id]
  (let [check-in-or-out
        (fn [op url callback]
          (op (str api-url url)
              {:format :json
               :keywords? true
               :headers
               (if (:token @session)
                 {:Authentication
                  (str "Bearer " (:token @session))}
                 {})
               :handler callback
               :error-handler (fn []
                                (swap! messages conj [:error "Check in failed. Please try again."]))}))]
    (fn [event-id attendee-id]
      (let [event (into {} (d/entity @events-db event-id))

            attendee
            (into {} (first (map (fn [[user-id attendee-id]]
                                   (merge (into {} (d/entity @users-db
                                                             user-id))
                                          (into {} (d/entity @attendees-db
                                                             attendee-id))))
                                 (d/q '[:find ?e ?a
                                        :in $ ?attendee-id
                                        :where
                                        [?a :AttendeeId ?attendee-id]
                                        [?a :UserId ?e]]
                                      @attendees-db
                                      attendee-id))))

            attendee-activities
            (d/q '[:find ?schedule-id ?activity-id
                   :in $activities $schedules $attendees ?event-id ?attendee-id
                   :where
                   [$activities ?activity-id :EventId ?event-id]
                   [$attendees  ?a :AttendeeId ?attendee-id]
                   [$attendees  ?a :UserId ?user-id]
                   [$schedules  ?schedule-id :UserId     ?user-id]
                   [$schedules  ?schedule-id :ActivityId ?activity-id]]
                 @activities-db
                 @schedules-db
                 @attendees-db
                 event-id
                 attendee-id)]
        (let [check-in
              (fn [attendee-id]
                (fn [callback]
                  (check-in-or-out PUT
                                   (str "/attendees/" attendee-id "/checkin")
                                   (fn []
                                     (prn "Checked in!")
                                     (attendees-endpoint :read nil callback)))))

              check-out
              (fn [attendee-id]
                (fn [callback]
                  (check-in-or-out DELETE
                                   (str "/attendees/" attendee-id "/checkin")
                                   (fn []
                                     (prn "Checked out!")
                                     (attendees-endpoint :read nil callback)))))

              activity-check-in
              (fn [schedule-id checked-in]
                (fn [callback]
                  (check-in-or-out PUT
                                   (str "/schedules/" schedule-id "/checkin")
                                   (fn []
                                     (prn "Checked in!")
                                     (schedules-endpoint :read nil callback)))))

              activity-check-out
              (fn [schedule-id checked-in]
                (fn [callback]
                  (check-in-or-out DELETE
                                   (str "/schedules/" schedule-id "/checkin")
                                   (fn []
                                     (prn "Checked out!")
                                     (schedules-endpoint :read nil callback)))))]
          (when (and (seq event) (seq attendee))
            [:div.sixteen.wide.column
             [:div.ui.segment
              [:div
               [:div.ui.vertical.segment
                [:h1.ui.header
                 (str (:FirstName attendee) " " (:LastName attendee))]]
               [:div.ui.vertical.segment
                [:div.ui.divided.items
                 [:div.item
                  [:div.content
                   [:a.header
                    {:href (event-route event)}
                    (:Name event)]
                   [:div.meta
                    [:b "Date: "]
                    (when event
                      (let [start (from-string (:StartDate event))
                            end   (from-string (:EndDate   event))]
                        (str (unparse datetime-formatter start)
                             (when (after? end start)
                               (str " to " (unparse datetime-formatter end))))))]
                   [:div.meta
                    [:b "Venue: "]
                    (:Venue event)]
                   [:div.description
                    (:Description event)]
                   [:div.extra
                    [action-button
                     {:class "right floated"}
                     (if (:CheckinTime attendee)
                       "Check out"
                       "Check in")
                     (if (:CheckinTime attendee)
                       (check-out attendee-id)
                       (check-in attendee-id))
                     (if (:CheckinTime attendee)
                       "Check in"
                       "Check out")]]]]]]
               [:div.ui.vertical.segment
                [:h3.ui.header
                 "Attendee Info"]
                [:table.ui.definition.table.attendee-info
                 [:tbody
                  [:tr
                   [:td "Email"]
                   [:td (:Email attendee)]]]]]
               [:div.ui.vertical.segment
                [:h3.ui.header
                 "Attendee Schedule"]
                [:table.ui.table
                 [:thead
                  [:tr
                   [:th "Start"]
                   [:th "End"]
                   [:th "Activity"]
                   [:th "Location"]
                   [:th]]]
                 [:tbody
                  (for [[schedule-id activity-id] attendee-activities]
                    ^{:key schedule-id}
                    (let [activity
                          (when activity-id
                            (d/entity @activities-db activity-id))
                          schedule
                          (when schedule-id
                            (d/entity @schedules-db schedule-id))]
                      (let
                        [checked-in (atom (not (nil? (:CheckinTime schedule))))]
                        [:tr
                         [:td {:noWrap true}
                          (when activity
                            (unparse datetime-formatter
                                     (from-string (:StartTime activity))))]
                         [:td {:noWrap true}
                          (when activity
                            (unparse datetime-formatter
                                     (from-string (:EndTime activity))))]
                         [:td (:Name activity)]
                         [:td (:Location activity)]
                         [:td.right.aligned {:noWrap true}
                          [action-button
                           {}
                           (if @checked-in
                             "Check out"
                             "Check in")
                           (if @checked-in
                             (activity-check-out schedule-id checked-in)
                             (activity-check-in schedule-id checked-in))
                           (if @checked-in
                             "Check in"
                             "Check out")]]])))]]]]]]))))))

(defn event-register-page [event-id]
  (let [form (atom {:Email (get-in @session [:user :Email])
                    :FirstName (get-in @session [:user :FirstName])
                    :LastName (get-in @session [:user :LastName])})
        validator (validation-set (presence-of :Email)
                                  (presence-of :FirstName)
                                  (presence-of :LastName)
                                  (format-of :Email :format #"@"))]
    (fn []
      (let [{:keys [Email FirstName LastName]}
            @form

            errors
            (validator @form)

            event
            (into {} (d/entity @events-db event-id))

            register
            (fn [form]
              (fn [callback]
                (when (empty? errors)
                  ; TODO: maybe this logic is wrong?MIght want to buy ticket even if not required
                  (if (:RequiresPayment event)
                    (renew-stripe-token!
                      (fn [] (attendees-endpoint :create
                                                 {:UserId (get-in @session [:user :UserId])
                                                  :EventId event-id
                                                  :Token (:stripe-token @session)
                                                  :Amount (:TicketPrice event)}
                                                 #(do
                                                    (callback)
                                                    (swap! session dissoc :stripe-token)
                                                    (js/location.replace (events-route))))))
                    (attendees-endpoint :create
                                        {:UserId (get-in @session [:user :UserId])
                                         :EventId event-id}
                                        #(do
                                           (callback)
                                           (js/location.replace (events-route))))))))]
        (when (seq event)
          [:div.ui.stackable.page.grid
           [:div.sixteen.wide.column
            [:div.ui.segment
             [:div.ui.vertical.segment
              [:h2.ui.dividing.header
               (str "Register for " (:Name event))]
              [:div.meta
               [:strong "Date: "]
               (let [start (from-string (:StartDate event))
                     end   (from-string (:EndDate   event))]
                 (str (unparse datetime-formatter start)
                      (when (after? end start)
                        (str " to " (unparse datetime-formatter end)))))]
              [:div.meta
               [:strong "Venue: "]
               (:Venue event)]
              (when (> (:TicketPrice event) 0)
                [:div.meta
                 [:strong "Ticket Price: "]
                 (goog.string.format "$%.2f" (:TicketPrice event))])
              [:div.description
               (:Description event)]]
             [:div.ui.vertical.segment
              [:form.ui.form
               [:div.one.field
                [:div.required.field
                 [:label "Email"]
                 [input-atom :text {:disabled true}
                  (r/wrap Email swap! form assoc :Email)]]]
               [:div.two.fields
                [:div.required.field
                 [:label "First Name"]
                 [input-atom :text {:disabled true}
                  (r/wrap FirstName swap! form assoc :FirstName)]]
                [:div.required.field
                 [:label "Last Name"]
                 [input-atom :text {:disabled true}
                  (r/wrap LastName swap! form assoc :LastName)]]]
               ; TODO: use TicketPrice > 0 instead of RequiresPayment
               (when (:RequiresPayment event)
                 [payments-component])]
              [:div.ui.divider]
              [action-button
               {:class (str "primary" (when (or (seq errors)
                                                (and (:RequiresPayment event)
                                                     (nil? (:payment-info @session))))
                                        " disabled"))
                :type :submit}
               "Register"
               (register @form)]]]]])))))

(defn event-schedule-page [event-id]
  (let [cart-activities (atom #{})

        add-activity!
        (fn [user-id activity-id]
          (schedules-endpoint :create
                              {:ActivityId activity-id
                               :UserId     user-id}
                              nil))

        remove-activity!
        (fn [schedule]
          (schedules-endpoint :delete schedule nil))]
    (fn [event-id]
      (let
        [event (into {} (d/entity @events-db event-id))

         scheduled-activities
         (d/q '[:find ?schedule-id ?activity-id
                :in $activities $schedules ?event-id ?user-id
                :where
                [$activities ?activity-id :EventId ?event-id]
                [$schedules  ?schedule-id :UserId     ?user-id]
                [$schedules  ?schedule-id :ActivityId ?activity-id]]
              @activities-db
              @schedules-db
              event-id
              (get-in @session [:user :UserId]))

         event-activities
         (d/q '[:find ?activity-id
                :in $ ?event-id
                :where
                [?activity-id :EventId ?event-id]]
              @activities-db
              event-id)

         unscheduled-activities
         (set/difference event-activities
                         (into #{} (map #(vector (second %))
                                        scheduled-activities))
                         @cart-activities)

         add-activity-to-cart!
         (fn [activity-id]
           (prn activity-id)
           (swap! cart-activities conj [activity-id]))

         remove-activity-from-cart!
         (fn [activity-id]
           (prn activity-id)
           (swap! cart-activities disj [activity-id]))

         add-cart-activities!
         (fn [e user-id cart-activities]
           (.preventDefault e)
           (let [activities (into [] (map (fn [[activity-id]]
                                            activity-id)
                                          @cart-activities))]
             (renew-stripe-token!
               (fn [] (schedules-endpoint :bulk
                                          {:UserId      user-id
                                           :Token       (:stripe-token @session)
                                           :ActivityIds activities}
                                          #(reset! cart-activities #{}))))))]
        (when (seq event)
          [:div.sixteen.wide.column
           [:div.ui.segment
            [:div.ui.vertical.segment
             [:h2.ui.header
              (str (get-in @session [:user :FirstName]) "'s Schedule for " (:Name event))]]
            [:div.ui.vertical.segment
             [:table.ui.table
              [:thead
               [:tr
                [:th "Start"]
                [:th "End"]
                [:th "Activity"]
                [:th "Location"]
                [:th]]]
              [:tbody
               (for [[schedule-id activity-id] scheduled-activities]
                 ^{:key schedule-id}
                 (let [activity
                       (when activity-id
                         (d/entity @activities-db activity-id))]
                   [:tr
                    [:td {:noWrap true}
                     (when activity
                       (unparse datetime-formatter
                                (from-string (:StartTime activity))))]
                    [:td {:noWrap true}
                     (when activity
                       (unparse datetime-formatter
                                (from-string (:EndTime activity))))]
                    [:td (:Name activity)]
                    [:td (:Location activity)]
                    [:td.right.aligned {:noWrap true}
                     [:div.ui.button
                      {:on-click #(remove-activity!
                                    (d/entity @schedules-db
                                              schedule-id))}
                      [:i.red.remove.icon] "Remove"]]]))]
              [:tfoot
               [:tr
                [:th {:colSpan "6"}
                 [:div.ui.small.labeled.icon.button
                  [:i.print.icon] "Print"]]]]]]
            [:div.ui.vertical.segment
             [:div.ui.divided.items
              (for [[activity-id] unscheduled-activities]
                ^{:key activity-id}
                (let [activity
                      (when activity-id
                        (d/entity @activities-db activity-id))]
                  [:div.item
                   [:div.content
                    [:a.header (:Name activity)]
                    [:div.meta [:strong "Location: "] (:Location activity)]
                    [:div.meta [:strong "Time: "]
                     (str (when activity
                            (unparse datetime-formatter
                                     (from-string
                                       (:StartTime activity))))
                          " - "
                          (when activity
                            (unparse datetime-formatter
                                     (from-string
                                       (:EndTime activity)))))]
                    (when (> (:TicketPrice activity) 0)
                      [:div.meta
                       [:strong "Ticket Price: "]
                       (goog.string.format "$%.2f" (:TicketPrice activity))])
                    [:div.description
                     (:Description activity)]
                    [:div.extra
                     (if (> (:TicketPrice activity) 0)
                       [:div.ui.right.floated.button
                        {:on-click #(add-activity-to-cart! (:ActivityId activity))}
                        "Add to cart"
                        [:i.right.chevron.icon]]
                       [:div.ui.right.floated.primary.button
                        {:on-click #(add-activity! (get-in @session [:user :UserId])
                                                   (:ActivityId activity))}
                        "Add"
                        [:i.right.chevron.icon]])]]]))]]]
           (when (seq @cart-activities)
             [:div.ui.segment
              [:div.ui.vertical.segment
               [:div.ui.vertical.segment
                [:h2.ui.dividing.header "Cart"]
                [:div.ui.divided.items
                 (for [[activity-id] @cart-activities]
                   ^{:key activity-id}
                   (let [activity
                         (when activity-id
                           (d/entity @activities-db activity-id))]
                     [:div.item
                      [:div.content
                       [:a.header (:Name activity)]
                       [:div.meta [:strong "Location: "] (:Location activity)]
                       [:div.meta [:strong "Time: "]
                        (str (when activity
                               (unparse datetime-formatter
                                        (from-string
                                          (:StartTime activity))))
                             " - "
                             (when activity
                               (unparse datetime-formatter
                                        (from-string
                                          (:EndTime activity)))))]
                       (when (> (:TicketPrice activity) 0)
                         [:div.meta
                          [:strong "Ticket Price: "]
                          (goog.string.format "$%.2f" (:TicketPrice activity))])
                       [:div.description
                        (:Description activity)]
                       [:div.extra
                        [:div.ui.right.floated.button
                         {:on-click #(remove-activity-from-cart! (:ActivityId activity))}
                         [:i.red.remove.icon]
                         "Remove"]]]]))]]
               [:div.ui.vertical.segment
                [:form.ui.form
                 [payments-component]]
                [:div.ui.divider]
                [:button.ui.primary.button
                 {:type :submit
                  :class (when (nil? (:payment-info @session))
                           "disabled")
                  :on-click #(add-cart-activities! % (get-in @session [:user :UserId]) cart-activities)}
                 "Confirm Payment and Add Activities"]]]])])))))

;TODO: Display only organizations you're a member of
(defn organizations-page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    [:div.ui.vertical.segment
     [:h1.ui.header
      "Organizations You're a Member of"
      [:a.ui.right.floated.small.button
       {:href (organization-add-route)}
       "Add Organization"]]]
    [:div.ui.vertical.segment
     [:div.ui.divided.items
      (for [organization (doall (map #(d/entity @organizations-db
                                                %)
                                     (d/q '[:find [?organization-id ...]
                                            :where [?organization-id]]
                                          @organizations-db)))]
        ^{:key (:OrganizationId organization)}
        [:div.item
         [:div.content
          [:a.header
           (:Name organization)]
          [:div.extra
           [:a.ui.right.floated.small.icon.button
            {:href (events-explore-route {:query-params
                                          (select-keys organization
                                                       [:OrganizationId])})} ; TODO: make this work
            "View events"]]]])]]]])

;TODO: Display only organizations you're not a member of
(defn organizations-explore-page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    [:div.ui.vertical.segment
     [:h1.ui.header
      "Explore Organizations"
      [:a.ui.right.floated.small.button
       {:href (organization-add-route)}
       "Add Organization"]]]
    [:div.ui.vertical.segment
     [:div.ui.divided.items
      (for [organization (doall (map #(d/entity @organizations-db
                                                %)
                                     (d/q '[:find [?organization-id ...]
                                            :where [?organization-id]]
                                          @organizations-db)))]
        ^{:key (:OrganizationId organization)}
        [:div.item
         [:div.content
          [:a.header
           (:Name organization)]
          [:div.extra
           [:a.ui.right.floated.small.icon.button
            {:href (str "#/events?organization="
                        (:OrganizationId organization))} ; TODO: make this work
            "View events"]]]])]]]])

;TODO: Display only organizations you own
(defn organizations-owned-page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    [:div.ui.vertical.segment
     [:h1.ui.header
      "Organizations You Own"
      [:a.ui.right.floated.small.button
       {:href (organization-add-route)}
       "Add Organization"]]]
    [:div.ui.vertical.segment
     [:div.ui.divided.items
      (for [organization (doall (map #(d/entity @organizations-db
                                                %)
                                     (d/q '[:find [?organization-id ...]
                                            :where [?organization-id]]
                                          @organizations-db)))]
        ^{:key (:OrganizationId organization)}
        [:div.item
         [:div.content
          [:a.header
           (:Name organization)]
          [:div.extra
           [:a.ui.right.floated.small.icon.button
            {:href (str "#/events?organization="
                        (:OrganizationId organization))} ; TODO: make this work
            "View events"]]]])]]]])

(defn organization-edit-page []
  (let [form (atom {})
        validator (validation-set (presence-of :Name))]
    (fn []
      (let [{:keys [Name]} @form
            errors (validator @form)
            create-organization
            (fn [form]
              (fn [callback]
                (organizations-endpoint :create
                                        (assoc form :AdminId (get-in @session [:user :UserId]))
                                        #(do
                                           (callback)
                                           (js/location.replace (organizations-route))))))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:form.ui.form
           [:div.ui.vertical.segment
            [:h2.ui.dividing.header
             "Add an Organization"]
            [:div.field
             [:div.required.field {:class (when (and Name (:Name errors))
                                            "error")}
              [:label "Organization Name"]
              [input-atom :text {} (r/wrap Name swap! form assoc :Name)]]]
            [action-button
             {:class (str "primary" (when (seq errors) " disabled"))
              :type :submit}
             "Add"
             (create-organization @form)]]]]]))))

(defn calendar-page []
  (let [user-activities
        (d/q '[:find ?schedule-id ?activity-id
               :in $schedules $activities ?user-id
               :where
               [$schedules  ?schedule-id :UserId     ?user-id]
               [$schedules  ?schedule-id :ActivityId ?activity-id]
               [$activities ?activity-id :ActivityId ?activity-id]]
             @schedules-db
             @activities-db
             (get-in @session [:user :UserId]))

        user-events
        (d/q '[:find ?event-id
               :in $ ?user-id
               :where
               [?attendee-id :UserId ?user-id]
               [?attendee-id :EventId ?event-id]]
             @attendees-db
             (get-in @session [:user :UserId]))]
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:h1.ui.header
       "Calendar"]
      [calendar {:events (vec (concat
                                (map (fn [[schedule-id activity-id]]
                                       (let [activity
                                             (when activity-id
                                               (d/entity @activities-db
                                                         activity-id))]
                                         {:title (:Name activity)
                                          :start (:StartTime activity)
                                          :end   (:EndTime activity)}))
                                     user-activities)
                                (map (fn [[event-id]]
                                       (let [event
                                             (when event-id
                                               (d/entity @events-db event-id))]
                                         {:title (:Name event)
                                          :start (:StartDate event)
                                          :end   (:EndDate event)
                                          :color "#8fdf82"}))
                                     user-events)))
                 :header {:left "title"
                          :center ""
                          :right "today prev,next month,agendaWeek,agendaDay"}
                 :defaultView "agendaWeek"}]]]))

(defn statistics-page []
  (let [event-id (atom 0)]
    (fn []
      (let [events
            (d/q '[:find ?name ?id
                   :where [?id :Name ?name]]
                 @events-db)

            attendees
            (d/q '[:find [?check-in-time ...]
                   :in $ ?event-id
                   :where
                   [?attendee-id :EventId ?event-id]
                   [?attendee-id :CheckinTime ?check-in-time]]
                 @attendees-db
                 @event-id)

            all-attendees
            (d/q '[:find [?attendee-id ...]
                   :in $ ?event-id
                   :where
                   [?attendee-id :EventId ?event-id]]
                 @attendees-db
                 @event-id)

            check-in-data
            (into (sorted-map) (frequencies (map to-long attendees)))

            check-in-data
            (sort-by first (vec (zipmap (keys check-in-data)
                                        (reduce #(conj %1 (+ (last %1) %2))
                                                []
                                                (vals check-in-data)))))

            activities
            (d/q '[:find ?name ?id
                   :in $ ?event-id
                   :where
                   [?id :Name ?name]
                   [?id :EventId ?event-id]]
                 @activities-db
                 @event-id)

            schedules
            (into []
                  (map
                    (fn [[activity-name activity-id]]
                      (vector activity-name
                              (count
                                (d/q '[:find [?check-in-time ...]
                                       :in $ ?activity-id
                                       :where
                                       [?schedule-id :ActivityId ?activity-id]
                                       [?schedule-id :CheckinTime ?check-in-time]]
                                     @schedules-db
                                     activity-id))))
                    activities))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:h1.ui.header
           "Statistics"]
          [:div.ui.form
           [:div.two.fields
            [:div.field
             [:label "Choose event:"]
             [input-atom :select {} events event-id identity int]]]]
          (when (seq all-attendees)
            [chart
             {:chart
              {:type "pie"}

              :title
              {:text "Attendance"}

              :tooltip
              {:pointFormat "{point.name}: <b>{point.percentage:.1f}%</b>"}

              :plotOptions
              {:pie {:cursor "pointer"
                     :dataLabels {:enabled true
                                  :format "<b>{point.name}</b>: {point.y}"}}}

              :series
              []}
             [["Attended" (count attendees)]
              ["Did Not Attend" (- (count all-attendees)
                                   (count attendees))]]])
          (when (seq check-in-data)
            [chart
             {:chart {:type "line"}
              :title {:text "Check in Times"}
              :xAxis {:type "datetime"
                      :dateTimeLabelFormats {:day "%e %b"}}
              :yAxis {:title {:text "Number checked in"}}
              :series [{:name "Checked in"}]}
             check-in-data])
          (when (and (seq activities) (seq schedules))
            [chart
             {:chart {:type "bar"}
              :title {:text "Activity attendance"}
              ;:xAxis {:categories (map first schedules)}
              :yAxis {:title {:text "Number of attendees"}
                      :allowDecimals false}
              :series [{:name "Attendee count"}]}
             schedules])]]))))

(defn payments-page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    ; TODO: expire stripe token if payment info changes
    [(with-meta identity
                {:component-did-mount (fn [] (.change (js/$ "#payments-form")
                                                      #_(swap! session assoc :stripe-token nil)
                                                      #(prn "changed")))})
     [:form#payments-form.ui.form
    [payments-component]]]]])


;; Frame
;; =============================================================================

(defn app-frame []
  [:div
   [stylesheet]
   [navbar-component]
   [:div.ui.page.grid
    [breadcrumbs-component]
    [messages-component]
    @current-page]])


;; History
;; =============================================================================

; must be called after routes have been defined
(defn hook-browser-navigation! []
  (doto (History.)
    (events/listen
     EventType/NAVIGATE
     (fn [event]
       (let [token (apply str (or (seq (.-token event)) '("/")))]
         (reset! location (str "#" token))
         (dispatch! token))))
    (.setEnabled true)))


;; Initialize app
;; =============================================================================

(defn mount-root []
  (r/render [app-frame] (.-body js/document)))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
