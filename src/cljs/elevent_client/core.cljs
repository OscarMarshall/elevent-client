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
      [cljs-time.coerce :refer [from-string]]
      [cljs-time.core :refer [after? at-midnight now]]
      [cljs-time.format :refer [formatter formatters unparse]]
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
         events-explore-page
         event-page
         event-edit-page
         event-register-page
         event-activities-page
         event-activities-explore-page
         event-activity-page
         event-activity-edit-page
         event-attendees-page
         event-attendee-page
         organizations-page
         organizations-explore-page
         organization-page
         organization-edit-page
         statistics-page
         sign-in-page
         sign-up-page)

(defonce current-page (atom #'home-page))

(secretary/set-config! :prefix "#")

(defroute home-route
  "/" []
  (reset! current-page [#'home-page]))

(defroute events-route
  "/events" []
  (reset! current-page [#'events-page]))

(defroute events-explore-route
  "/events/explore" []
  (reset! current-page [#'events-explore-page]))

(defroute event-add-route
  "/events/add" []
  (reset! current-page [#'event-edit-page]))

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

(defroute statistics-route
  "/statistics" []
  (reset! current-page [#'statistics-page]))

(defroute sign-in-route "/sign-in" []
  (reset! current-page [#'sign-in-page]))

(defroute sign-up-route "/sign-up" []
  (reset! current-page [#'sign-up-page]))


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
                         event-attendees-breadcrumbs]))

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
       (for [event (map (partial d/entity @events-db)
                        (d/q '[:find [?event-id ...]
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
            " "
            (let [start (from-string (:StartDate event))
                  end   (from-string (:EndDate   event))]
              (str (unparse datetime-formatter start)
                   (when (after? end start)
                     (str " to " (unparse datetime-formatter end)))))]
           [:div.meta
            [:strong "Venue:"]
            " "
            (:Venue event)]
           [:div.description
            (:Description event)]
           [:div.extra
            [:a.ui.right.floated.button {:href (event-activities-route)}
             "Your activities"
             [:i.right.chevron.icon]]]]])]]]
    [:div.ui.dimmer {:class (when (empty? @events) "active")}
     [:div.ui.loader]]]])

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

(defn event-register-page [event-id]
  (let [form (atom {:Email (get-in @session [:user :Email])
                    :FirstName (get-in @session [:user :FirstName])
                    :LastName (get-in @session [:user :LastName])})
        validator (validation-set (presence-of :Email)
                                  (presence-of :FirstName)
                                  (presence-of :LastName)
                                  (format-of :Email :format #"@"))
        event (d/entity @events-db event-id)]
    (fn []
      (let [{:keys [Email FirstName LastName]}
            @form

            errors
            (validator @form)

            register
            (fn [form]
              (attendees-endpoint :create
                                  {:UserId (get-in @session [:user :UserId])
                                   :EventId event-id}
                                  #(js/location.replace (events-route))))]
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
               [input-atom :text (r/wrap Email swap! form assoc :Email)]]]
             [:div.two.fields
              [:div.required.field
               [:label "First Name"]
               [input-atom :text (r/wrap FirstName swap! form assoc :FirstName)]]
              [:div.required.field
               [:label "Last Name"]
               [input-atom :text (r/wrap LastName swap! form assoc :LastName)]]]]
            [:button.ui.primary.button
             {:type :submit :on-click #(register @form)}
             "Register"]]]]]))))

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
         (secretary/dispatch! token))))
    (.setEnabled true)))


;; Initialize app
;; =============================================================================

(defn init! []
  (hook-browser-navigation!)
  (r/render [app-frame] (.-body js/document)))
