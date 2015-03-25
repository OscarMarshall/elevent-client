(ns elevent-client.core
    (:require
      [clojure.set :as set]
      [clojure.string :as str]

      [goog.crypt.base64 :as b64]
      [goog.events :as events]
      [goog.history.EventType :as EventType]

      [cljsjs.react :as react]
      [reagent.core :as r :refer [atom create-class render-component]]

      [alandipert.storage-atom :refer [local-storage]]
      [ajax.core :refer [DELETE GET POST PUT]]
      [cljs-time.coerce :refer [from-string from-long to-long]]
      [cljs-time.core :refer [after? at-midnight now plus hours]]
      [cljs-time.format :refer [formatter formatters unparse parse]]
      [datascript :as d]
      [garden.core :refer [css]]
      [secretary.core :as secretary :refer-macros [defroute]]
      [validateur.validation :refer [format-of
                                     presence-of
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
(def input-date-time-format "hh:mm a YYYY-MM-dd")
(def date-formatter (formatter date-format))
(def time-formatter (formatter time-format))
(def datetime-formatter (formatter datetime-format))
(def input-date-time-formatter (formatter input-date-time-format))



;; Location
;; =============================================================================

(def location (atom ""))


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
                              (= operation :update))
                      params)

                    :handler
                    (if (= operation :read)
                      (fn [json]
                        (reset! state json)
                        (when handler (handler json)))
                      (fn [json]
                        (when handler (handler json))
                        (dispatch! :read nil nil nil)))

                    :error-handler
                    (fn [error]
                      (when (= (:failure error) :timeout)
                        (add-message! :negative (str uri " timed out")))
                      (when error-handler (error-handler error)))}]
       (let [check-id (fn [op] (if (contains? params element-id)
                                 (op (str uri "/" (params element-id)) options)
                                 (throw (str "Element doesn't contain key \""
                                             (prn-str element-id)
                                             "\": "
                                             (prn-str params)))))]
         (case operation
           :create (POST uri options)
           :read   (GET  uri options)
           :update (check-id PUT)
           :delete (check-id DELETE)
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
         events-page
         sign-in-page
         sign-up-page
         events-explore-page
         event-page
         event-edit-page
         event-register-page
         event-activities-explore-page
         event-activities-page
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
         statistics-component)

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

(defroute events-route
  "/events" []
  (if (:token @session)
    (reset! current-page [#'events-page])
    (location.replace (events-explore-route))))

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

(defroute event-activities-explore-route
  "/events/:EventId/activities/explore" [EventId]
  (reset! current-page [#'event-activities-explore-page (int EventId)]))

(defroute event-activities-route
  "/events/:EventId/activities" [EventId]
  (reset! current-page [#'event-activities-page (int EventId)]))

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

(def dispatch!
  (secretary/uri-dispatcher [home-route
                             sign-in-route
                             sign-up-route
                             events-explore-route
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
                             statistics-route]))


;; User Account
;; =============================================================================

(defn sign-in! [form]
  (let [{:keys [email password]} form
        auth-string (b64/encodeString (str email ":" password))]
    (GET (str api-url "/token")
         {:format          :json
          :response-format :json
          :keywords?       true
          :headers         {:Authorization (str "Basic " auth-string)}
          :handler         (fn [response]
                             (swap! session assoc :token (:Token response))
                             (swap! session assoc-in [:user :Email] email)
                             (refresh!)
                             (reset! messages {})
                             (add-message! :positive "Sign in succeeded")
                             (js/location.replace (events-route)))})))

(defn sign-out! []
  (swap! session dissoc :token :user)
  (refresh!)
  (set! js/location (home-route)))

(add-watch users-db
           :find-user
           (fn [_ db _ _]
             (when-let [email (get-in @session [:user :Email])]
               (swap! session
                      assoc
                      :user
                      (->> email
                           (d/q '[:find ?user-id
                                  :in $ ?email
                                  :where [?user-id :Email ?email]]
                                @db)
                           ffirst
                           (d/entity @db)
                           seq
                           (into {}))))))


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

(defn input-atom
  ([type options state in out]
   (let [in  (or in  identity)
         out (or out identity)]
     (r/create-class
       {:component-did-update
        (fn [this]
          (when (= type :select)
            (-> this
                r/dom-node
                js/jQuery
                (.dropdown (clj->js {:onChange #(when % (reset! state
                                                                (out %)))})))))

        :reagent-render
        (fn render
          ([_ options state _ _]
           (let [attributes {:value     (in @state)
                             :on-change #(reset! state
                                                 (out (.-value (.-target %))))}]
             (case type
               :textarea [:textarea attributes]
               :select [:div.ui.dropdown.selection
                        [:input (assoc attributes :type :hidden)]
                        [:div.text "None"]
                        [:i.dropdown.icon]
                        [:div.menu
                         (for [[name value] options]
                           ^{:key (or value 0)} [:div.item {:data-value value}
                                                 name])]]
               [:input (assoc attributes :type type)])))
          ([_ state _ _]
           (render nil nil state nil nil))
          ([_ options state]
           (render nil options state nil nil))
          ([_ state]
           (render nil nil state nil nil)))})))
  ([type state in out]
   (input-atom type nil state in out))
  ([type options state]
   (input-atom type options state nil nil))
  ([type state]
   (input-atom type nil state nil nil)))


;; Compenents
;; =============================================================================

(defn navbar-component []
  [:nav.ui.fixed.menu
   [:a.logo.item {:href (home-route)}
    [:img {:src "images/logo-menu.png"}]]
   [:a.item {:href (events-explore-route)}
    [:i.rocket.icon]
    "Explore"]
   (when (:token @session)
     [:a.item {:href "#/events"}
      [:i.ticket.icon]
      "Events"])
   [:a.item {:href "#/organizations"}
    [:i.building.icon]
    "Organizations"]
   [:a.item {:href "#/calendar"}
    [:i.calendar.icon]
    "Calendar"]
   [:a.item {:href "#/statistics"}
    [:i.bar.chart.icon]
    "Statistics"]
   (if (:token @session)
     [:div.right.menu
      [:a.item {:on-click sign-out!}
       [:i.sign.out.icon]
       "Sign out"]]
     [:div.right.menu
      [:a.item {:href (sign-in-route)}
       [:i.sign.in.icon]
       "Sign in"]
      [:a.item {:href (sign-up-route)}
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
          (let [env (assoc env :AttendeeId fragment)
                entity (d/entity @attendees-db (int fragment))]
            [[(str (:FirstName entity) (:LastName entity))
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


;; Views
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

; TODO: abstract events and events-explore into single component
(defn events-page []
  (let [leave-event
        (fn [attendee-id]
          ; TODO: this doesn't delete schedules
          (attendees-endpoint :delete (d/entity @attendees-db attendee-id) nil))]
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:div
       [:div.ui.vertical.segment
        [:div.ui.two.column.grid
         [:div.column
          [:h1.ui.header "Your Events"]]
         [:div.right.aligned.column
          [:a.ui.tiny.labeled.icon.button {:href (event-add-route)}
           [:i.plus.icon]
           "Add event"]]]]
       [:div.ui.vertical.segment
        [:div.ui.divided.items
         (for [event (map (fn [[event-id attendee-id]]
                            (merge
                              (into {} (d/entity @events-db event-id))
                              {:AttendeeId attendee-id}))
                          (d/q '[:find ?event-id ?attendee-id
                                 :in $ ?user-id
                                 :where
                                 [?attendee-id :UserId  ?user-id]
                                 [?attendee-id :EventId ?event-id]]
                               @attendees-db
                               (get-in @session [:user :UserId])))]
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
               "Leave event"]]]])]]]
      [:div.ui.dimmer {:class (when (empty? @events) "active")}
       [:div.ui.loader]]]]))

(defn events-explore-page []
  (let [attending-events (d/q '[:find [?event-id ...]
                                :in $events $attendees ?user-id
                                :where
                                [$events ?event-id]
                                [$attendees ?attendee-id :EventId ?event-id]
                                [$attendees ?attendee-id :UserId ?user-id]]
                              @events-db
                              @attendees-db
                              (get-in @session [:user :UserId]))
        unattending-events (set/difference (into #{}
                                                 (d/q '[:find [?event-id ...]
                                                        :where [?event-id]]
                                                      @events-db))
                                           (into #{} attending-events))]
    [:div.sixteen.wide.column
     [:div.ui.segment
      [:div
       [:div.ui.vertical.segment
        [:div.ui.two.column.grid
         [:div.column
          [:h1.ui.header "Explore Events"]]
         [:div.right.aligned.column
          [:a.ui.tiny.labeled.icon.button {:href (event-add-route)}
           [:i.plus.icon]
           "Add event"]]]]
       [:div.ui.vertical.segment
        [:div.ui.divided.items
         (for [event (map (partial d/entity @events-db) unattending-events)]
           ^{:key (:EventId event)}
           [:div.item
            [:div.content
             [:a.header {:href (event-route event)}
              (:Name event)]
             [:div.meta
              [:strong "Date:"]
              (let [start (from-string (:StartDate event))
                    end   (from-string (:EndDate   event))]
                (str " " (unparse datetime-formatter start)
                     (when (after? end start)
                       (str " to " (unparse datetime-formatter end)))))]
             [:div.meta
              [:strong "Venue:"]
              " "
              (:Venue event)]
             [:div.description
              (:Description event)]
             [:div.extra
              [:a.ui.right.floated.tiny.button
               {:href (event-register-route event)}
               "Register"
               [:i.right.chevron.icon]]]]])]]]
      [:div.ui.dimmer {:class (when-not @events "active")}
       [:div.ui.loader]]]]))

(defn event-edit-page []
  (let [form (atom {})
        validator (validation-set (presence-of :Name)
                                  (presence-of :Organization)
                                  (presence-of :Venue)
                                  (presence-of :StartDate)
                                  (presence-of :EndDate))
        clone-id (atom 0)]
    (add-watch clone-id :clone
               (fn [_ _ _ id]
                 (when-not (zero? (int id))
                   (reset! form (dissoc (->> id
                                             int
                                             (d/entity @events-db)
                                             seq
                                             (into {}))
                                        :EventId)))))
    (fn []
      (let [{:keys [Name OrganizationId Venue StartDate EndDate Description]}
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
              (when (empty? errors)
                (events-endpoint :create
                                 form
                                 #(set! js/location (events-explore-route)))))]
        [:div.sixteen.wide.column
         [:div.ui.segment
          [:form.ui.form
           [:div.ui.vertical.segment
            [:h2.ui.dividing.header "Add an Event"]
            [:p (prn-str @form)]
            [:div.two.fields
             [:div.required.field {:class (when (and Name (:Name errors))
                                            "error")}
              [:label "Name"]
              [input-atom :text (r/wrap Name swap! form assoc :Name)]]
             [:div.field
              [:label "Clone From"]
              [input-atom :select clonable-events clone-id]]]
            [:div.two.fields
             [:div.field
              [:label "Organization"]
              [input-atom :select associated-organizations
               (r/wrap OrganizationId swap! form assoc :OrganizationId)]]
             [:div.required.field {:class (when (and Venue (:Venue errors))
                                            "error")}
              [:div.field
               [:label "Venue"]
               [input-atom :text (r/wrap Venue swap! form assoc :Venue)]]]]
            [:div.two.fields
             [:div.field
              [:label "Start Date"]
              [input-atom :datetime-local
               (r/wrap StartDate swap! form assoc :StartDate)
               #(or % (unparse (:date-hour-minute formatters) (now)))
               #(unparse (:date-hour-minute formatters) (from-string %))]]
             [:div.field
              [:label "End Date"]
              [input-atom :datetime-local
               (r/wrap EndDate swap! form assoc :EndDate)
               #(or % (unparse (:date-hour-minute formatters) (now)))
               #(unparse (:date-hour-minute formatters) (from-string %))]]]
            [:div.field
             [:label "Description"]
             [input-atom :textarea
              (r/wrap Description swap! form assoc :Description)]]
            [:button.ui.primary.button {:class (when (seq errors) "disabled")
                                        :type :submit
                                        :on-click #(create-event @form)}
             "Add"]]]]]))))

(defn event-activity-edit-page [event-id]
  (let [form (atom {:EventId event-id})
        validator (validation-set (presence-of :Name)
                                  (presence-of :StartTime)
                                  (presence-of :EndTime)
                                  (format-of   :EnrollmentCap :format #"[0-9_]"
                                                              :allow-nil true
                                                              :allow-blank true
                                                              :message "Please enter a number"))
        reset-form!
        (fn []
          (reset! form (atom {:EventId event-id})))]
    (fn [event-id]
      (let [{:keys [Name Location EnrollmentCap StartTime EndTime Description]}
            @form

            errors
            (validator @form)

            event
            (into {} (seq (d/entity @events-db event-id)))

            activities
            (map #(d/entity @activities-db %)
                 (d/q '[:find [?e ...]
                        :in $ ?event-id
                        :where
                        [?e :EventId ?event-id]]
                      @activities-db
                      event-id))

            create-activity
            (fn [form]
              (when (empty? errors)
                (activities-endpoint :create
                                     (let [start-time (:StartTime form)
                                           end-time   (:EndTime form)]
                                       (assoc form
                                         :StartTime (unparse (:date-hour-minute-second formatters)
                                                             (from-string start-time))
                                         :EndTime (unparse (:date-hour-minute-second formatters)
                                                           (from-string end-time))))
                                     #(reset-form!))))]
        (when (seq event)
          [:div.sixteen.wide.column
           [:div.ui.segment
            [:div.ui.vertical.segment
             [:h2.ui.header
              (:Name event)]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              "Add activity"]
             [:form.ui.form
              [:div.one.field
               [:div.required.field {:class (when (and Name (:Name errors))
                                              "error")}
                [:label "Name"]
                [input-atom :text (r/wrap Name swap! form assoc :Name)]]]
              [:div.two.fields
               [:div.field
                [:label "Location"]
                [input-atom :text (r/wrap Location swap! form assoc :Location)]]
               [:div.field {:class (when (and EnrollmentCap (:EnrollmentCap errors))
                                     "error")}
                [:label "Enrollment Cap"]
                [input-atom :text (r/wrap EnrollmentCap swap! form assoc :EnrollmentCap)]]]
              [:div.two.fields =
               [:div.required.field {:class (when (and StartTime (:StartTime errors))
                                              "error")}
                [:label "Start Time"]
                [input-atom :datetime-local
                 (r/wrap StartTime swap! form assoc :StartTime)
                 #(or % (unparse (:date-hour-minute formatters) (now))) ; todo: date inputs clear on input
                 #(unparse (:date-hour-minute formatters) (from-string %))]]
               [:div.required.field {:class (when (and EndTime (:EndTime errors))
                                              "error")}
                [:label "End Time"]
                [input-atom :datetime-local
                 (r/wrap EndTime swap! form assoc :EndTime)
                 #(or % (unparse (:date-hour-minute formatters) (now)))
                 #(unparse (:date-hour-minute formatters) (from-string %))]]]
              [:div.field
               [:label "Description"]
               [input-atom :textarea
                (r/wrap Description swap! form assoc :Description)]]
              [:button.ui.primary.button {:class (when (seq errors) "disabled")
                                          :type :submit
                                          :on-click #(create-activity @form)}
               "Add"]]]
            [:div.ui.vertical.segment
             [:h2.ui.header
              "Activities"]
             [:table.ui.table
              [:thead
               [:tr
                [:th "Start Time"]
                [:th "End Time"]
                [:th "Activity"]
                [:th "Location"]]]
              [:tbody
               (for [activity activities]
                 ^{:key (:ActivityId activity)}
                 [:tr
                  [:td (let [start (from-string (:StartTime activity))]
                         (unparse datetime-formatter start))]
                  [:td (let [end   (from-string (:EndTime   activity))]
                         (unparse datetime-formatter end))]
                  [:td (:Name activity)]
                  [:td (:Location activity)]])]]]]])))))

(defn event-page [event-id]
  (let [event (into {} (seq (d/entity @events-db event-id)))

        activities (map #(d/entity @activities-db %)
                        (d/q '[:find [?e ...]
                               :in $ ?event-id
                               :where
                               [?e :EventId ?event-id]]
                             @activities-db
                             event-id))
        attendees (take 10
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
                                  event-id)))]
    (when (seq event)
      [:div.sixteen.wide.column
       [:div.ui.segment
        [:div.ui.vertical.segment
         [:h2.ui.dividing.header
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
        [:div.ui.vertical.segment
         [:h2.ui.header
          "Activities"]
         [:table.ui.table
          [:thead
           [:tr
            [:th "Start"]
            [:th "End"]
            [:th "Activity"]
            [:th "Location"]]]
          [:tbody
           (for [activity activities]
             ^{:key (:ActivityId activity)}
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
              [:td (:Location activity)]])]
          [:tfoot
           [:tr
            [:th {:colSpan "4"}
             [:a.ui.right.floated.small.labeled.icon.button
              {:href (event-activity-add-route event)}
              [:i.edit.icon]
              "Edit"]]]]]]
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

(defn event-attendee-page [event-id attendee-id]
  (let [check-in-or-out
        (fn [op url init callback]
          (init)
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
                                (swap! messages conj [:error "Check in failed. Please try again."]))}))

        button-loading? (atom false)]
    (fn [event-id attendee-id]
      (let [event (into {} (seq (d/entity @events-db event-id)))

            attendee
            (atom (into {} (seq
                             (first (map (fn [[user-id attendee-id]]
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
                                              attendee-id))))))

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
        (let [button-text (atom (if @button-loading?
                                  [:i.spinner.loading.icon]
                                  (if (:CheckinTime @attendee)
                                    "Check out"
                                    "Check in")))

              check-in
              (fn [attendee-id]
                (check-in-or-out PUT
                                 (str "/attendees/" attendee-id "/checkin")
                                 #(reset! button-loading? true)
                                 (fn []
                                   (prn "Checked in!")
                                   (attendees-endpoint :read nil #(reset! button-loading? false)))))

              check-out
              (fn [attendee-id]
                (check-in-or-out DELETE
                                 (str "/attendees/" attendee-id "/checkin")
                                 #(reset! button-loading? true)
                                 (fn []
                                   (prn "Checked out!")
                                   (attendees-endpoint :read nil #(reset! button-loading? false)))))

              activity-check-in
              (fn [schedule-id checked-in callback]
                (check-in-or-out PUT
                                 (str "/schedules/" schedule-id "/checkin")
                                 #()
                                 (fn []
                                   (prn "Checked in!")
                                   (schedules-endpoint :read nil #(do
                                                                    ;(reset! checked-in true)
                                                                    (callback))))))

              activity-check-out
              (fn [schedule-id checked-in callback]
                (check-in-or-out DELETE
                                 (str "/schedules/" schedule-id "/checkin")
                                 #()
                                 (fn []
                                   (prn "Checked out!")
                                   (schedules-endpoint :read nil #(do
                                                                    ;(reset! checked-in false)
                                                                    (callback))))))]
          (when (seq event)
            [:div.sixteen.wide.column
             [:div.ui.segment
              [:div
               [:div.ui.vertical.segment
                [:h1.ui.header
                 (str (:FirstName @attendee) " " (:LastName @attendee))]]
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
                    [:div.ui.right.floated.button
                     {:on-click #(if (:CheckinTime @attendee)
                                   (check-out attendee-id)
                                   (check-in attendee-id))}
                     @button-text]]]]]]
               [:div.ui.vertical.segment
                [:h3.ui.header
                 "Attendee Info"]
                [:table.ui.definition.table.attendee-info
                 [:tbody
                  [:tr
                   [:td "Email"]
                   [:td (:Email @attendee)]]]]]
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
                        [checked-in (atom (not (nil? (:CheckinTime schedule))))
                         checking-in (atom false)]
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
                           {:on-click (fn []
                                        (reset! checking-in true)
                                        (if @checked-in
                                          (activity-check-out schedule-id checked-in #(reset! checking-in false))
                                          (activity-check-in schedule-id checked-in #(reset! checking-in false))))}
                           (if @checking-in
                             [:i.spinner.loading.icon] ; todo: not working
                             (if @checked-in
                               "Check out"
                               "Check in"))]]])))]]]]]]))))))

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
            (into {} (seq (d/entity @events-db event-id)))

            register
            (fn [form]
              (when (empty? errors)
                (attendees-endpoint :create
                                    {:UserId (get-in @session [:user :UserId])
                                     :EventId event-id}
                                    #(js/location.replace (events-route)))))]
        (when (seq event)
          [:div.ui.stackable.page.grid
           [:div.sixteen.wide.column
            [:div.ui.segment
             [:div.ui.vertical.segment
              [:h2.ui.dividing.header
               (str "Register for " (:Name event))]
              [:div.meta
               [:strong "Date:"]
               (let [start (from-string (:StartDate event))
                     end   (from-string (:EndDate   event))]
                 (str " " (unparse datetime-formatter start)
                      (when (after? end start)
                        (str " to " (unparse datetime-formatter end)))))]
              [:div.meta
               [:strong "Venue:"]
               " "
               (:Venue event)]
              [:div.description
               (:Description event)]]
             [:div.ui.vertical.segment
              [:form.ui.form
               [:div.one.field
                [:div.required.field
                 [:label "Email"]
                 [input-atom :text
                  (r/wrap Email swap! form assoc :Email)]]]
               [:div.two.fields
                [:div.required.field
                 [:label "First Name"]
                 [input-atom :text
                  (r/wrap FirstName swap! form assoc :FirstName)]]
                [:div.required.field
                 [:label "Last Name"]
                 [input-atom :text
                  (r/wrap LastName swap! form assoc :LastName)]]]]
              [:button.ui.primary.button
               {:type :submit
                :class (when (seq errors) "disabled")
                :on-click #(register @form)}
               "Register"]]]]])))))

(defn event-schedule-page [event-id]
  (let [event (into {} (seq (d/entity @events-db event-id)))

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
        (set/difference event-activities (into #{} (map #(vector (second %))
                                                    scheduled-activities)))

        add-activity!
        (fn [user-id activity-id]
          (schedules-endpoint :create
                              {:ActivityId activity-id
                               :UserId     user-id}
                              nil))

        remove-activity!
        (fn [schedule]
          (schedules-endpoint :delete schedule nil))]
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
             [:div.meta (:Location activity)]
             [:div (str (when activity
                          (unparse datetime-formatter
                                   (from-string
                                     (:StartTime activity))))
                        " - "
                        (when activity
                          (unparse datetime-formatter
                                   (from-string
                                     (:EndTime activity)))))]
             [:div.description
              (:Description activity)]
             [:div.extra
              [:div.ui.right.floated.primary.button
               {:on-click #(add-activity! (get-in @session [:user :UserId])
                                          (:ActivityId activity))}
               "Add"
               [:i.right.chevron.icon]]]]]))]]]])))

(defn organizations-page []
  [:div.sixteen.wide.column
   [:div.ui.segment
    [:div.ui.vertical.segment
     [:h1.ui.header
      "Organizations"
      [:a.ui.right.floated.small.button
       {:href (organization-add-route)}
       "Add Organization"]]]
    [:div.ui.vertical.segment
     [:div.ui.divided.items
      (for [organization (map #(d/entity @organizations-db
                                         %)
                              (d/q '[:find [?organization-id ...]
                                     :where [?organization-id]]
                                   @organizations-db))]
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
              (organizations-endpoint :create
                                      form
                                      #(set! js/location "#/organizations")))]
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
              [input-atom :text (r/wrap Name swap! form assoc :Name)]]]
            [:button.ui.primary.button
             {:type :submit
              :class (when (seq errors) "disabled")
              :on-click #(create-organization @form)}
             "Add"]]]]]))))

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
      [(with-meta identity
                  {:component-did-mount
                   (fn []
                     (.fullCalendar (js/$ "#calendar")
                                    (clj->js
                                      {:events (vec (concat
                                                      (map (fn [[schedule-id activity-id]]
                                                             (let [activity
                                                                   (when activity-id
                                                                     (d/entity @activities-db activity-id))]
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
                                       :defaultView "agendaWeek"})))})
       [:div#calendar]]]]))

(defn pie-chart-config [attendees all-attendees]
  {:chart {:type "pie"}
   :title {:text "Attendance"}
   :tooltip {:pointFormat "{point.name}: <b>{point.percentage:.1f}%</b>"}
   :plotOptions {:pie {:cursor "pointer"
                       :dataLabels {
                                    :enabled true
                                    :format "<b>{point.name}</b>: {point.y}"
                                    }}}
   :series [{:data [
                    ["Attended" (count attendees)]
                    ["Did Not Attend" (- (count all-attendees) (count attendees))]]}]})

(defn time-chart-config [check-in-data]
  {:chart {:type "line"}
   :title {:text "Check in Times"}
   :xAxis {:type "datetime"
           :dateTimeLabelFormats {:day "%e %b"}}
   :yAxis {:title {:text "Number checked in"}}
   :series [{:name "Checked in"
             :data check-in-data}]})

(defn get-time-counts [pairs prev-count]
  (if (empty? pairs)
    (list)
    (let [check-in-time (ffirst pairs)
          attendee-count (count (second (first pairs)))]
      (cons [check-in-time (+ attendee-count prev-count)]
            (get-time-counts (rest pairs) (+ attendee-count prev-count))))))

(defn statistics-page-did-mount [event-id]
  (let
    [attendees
     (d/q '[:find ?attendee-id ?check-in-time
            :in $events $attendees ?event-id
            :where
            [$events ?event-id :EventId ?event-id]
            [$attendees ?attendee-id :EventId ?event-id]
            [$attendees ?attendee-id :CheckinTime ?check-in-time]]
          @events-db
          @attendees-db
          event-id)

     all-attendees
     (d/q '[:find ?attendee-id
            :in $events $attendees ?event-id
            :where
            [$events ?event-id :EventId ?event-id]
            [$attendees ?attendee-id :EventId ?event-id]]
          @events-db
          @attendees-db
          event-id)

     check-in-data
     (get-time-counts
       (into
         []
         (into
           (sorted-map)
           (apply
             (partial merge-with concat)
             (map (fn [[attendee-id check-in-time]]
                    {(to-long check-in-time)
                     (list attendee-id)})
                  attendees))))
       0)]
    (do
      (js/$ (fn []
              (.highcharts (js/$ "#graph")
                           (clj->js (pie-chart-config attendees all-attendees)))))
      (js/$ (fn []
              (.highcharts (js/$ "#graph2")
                           (clj->js (time-chart-config check-in-data)))))
      (prn check-in-data)
      (prn event-id))))

(defn statistics-page []
  (fn []
    (let [events
          (d/q '[:find ?name ?id
                 :where [?id :Name ?name]]
               @events-db)]
      [(with-meta identity
                  {:component-did-mount (fn [] (render-component [statistics-component (first
                                                                                         (map (fn [[event-name event-id]]
                                                                                                event-id)
                                                                                              events))]
                                                                 (.getElementById js/document "graph")))})
       [:div.sixteen.wide.column
        [:div.ui.segment
         [:h1.ui.header
          "Statistics"]
         [:div.ui.form
          [:div.two.fields
           [:div.field
            [:label "Choose event:"]
            [:select.ui {:type "select" :on-change #(statistics-page-did-mount (int (.-value (.-target %))))}
             (for [[event-name event-id] events]
               ^{:key event-id}
               [:option {:value event-id}
                event-name])]]]]
         [:div#graph {:style {:min-width "310px" :max-width "800px"
                              :height "250px" :margin "0 auto"}}]
         [:div#graph2 {:style {:min-width "310px" :max-width "800px"
                               :height "400px" :margin "0 auto"}}]]]])))

(defn statistics-component [event-id]
  (create-class {:reagent-render statistics-page
                 :component-did-mount #(statistics-page-did-mount event-id)}))

(defn sign-in-page []
  (let [form (atom {})
        validator (validation-set (format-of :email :format #"@")
                                  (presence-of :password))]
    (fn []
      (let [{:keys [email password]} @form
            errors                   (validator @form)]
        [:div.eight.wide.centered.column
         [:div.ui.segment
          [:h1.ui.dividing.header "Sign In"]
          [:form.ui.form {:on-submit (fn [e]
                                       (.preventDefault e)
                                       (when (valid? validator @form)
                                         (sign-in! @form)))}
           [:div.two.fields
            [:div.required.field {:class (when (and email (:email errors))
                                           "error")}
             [:label "Email"]
             [:div.ui.icon.input
              [input-atom :email
               (r/wrap email swap! form assoc :email)]
              [:i.mail.icon]]]
            [:div.required.field
             [:label "Password"]
             [:div.ui.icon.input
              [input-atom :password
               (r/wrap password swap! form assoc :password)]
              [:i.lock.icon]]]]
           [:button.ui.primary.button {:type :submit} "Sign in"]]]]))))


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
       (let [token (.-token event)]
         (reset! location token)
         (dispatch! token))))
    (.setEnabled true)))


;; Initialize app
;; =============================================================================

(defn mount-root []
  (r/render [app-frame] (.-body js/document)))

(defn init! []
  (hook-browser-navigation!)
  (mount-root))
