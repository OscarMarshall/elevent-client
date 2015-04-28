;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.components.breadcrumbs
  (:require [clojure.string :as str]

            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]))

(defn breadcrumber
  "Creates a breadcrumber function. If the resulting function receives two
  argements, it returns the first argument, otherwise, it returns a new
  breadcrumber. The supplied lambda must take an environment and a fragment, and
  must return a sequence of a piece of the result, a new environment, and a
  lambda for a new breadcrumber."
  [lambda]
  (fn
    ([results _] results)
    ([results env fragment]
     (let [[result env continuation] (lambda env fragment)]
       (partial (breadcrumber continuation)
                (conj results result)
                env)))))

(defn event-activity-breadcrumbs
  "Breadcrumber lambda for when we're at '/events/:id/actvities/:id'."
  [env _]
  [["Edit" (routes/event-activity-edit env)] env nil])

(defn event-activities-breadcrumbs
  "Breadcrumber lambda for when we're at '/events/:id/activities'."
  [env fragment]
  (case fragment
    "explore" [["Explore" (routes/event-activities-explore env)]
               env
               nil]
    "add" [["Add" (routes/event-activity-add env)] env nil]
    (let [env (assoc env :ActivityId fragment)]
      [[(:Name (d/entity @api/activities-db (int fragment)))
        (routes/event-activity env)]
       env
       event-activity-breadcrumbs])))

(defn event-group-breadcrumbs
  "Breadcrumber lambda for when we're at '/events/:id/groups/:id'."
  [env _]
  [["Edit" (routes/event-group-edit env)] env nil])

(defn event-groups-breadcrumbs
  "Breadcrumber lambda for when we're at '/events/:id/groups'."
  [env fragment]
  (case fragment
    "explore" [["Explore" (routes/event-groups-explore env)]
               env
               nil]
    "add" [["Add" (routes/event-group-add env)] env nil]
    (let [env (assoc env :GroupId fragment)]
      [[(:Name (d/entity @api/groups-db (int fragment)))
        (routes/event-group env)]
       env
       event-group-breadcrumbs])))

(defn event-attendees-breadcrumbs
  "Breadcrumber lambda for when we're at '/events/:id/attendees'."
  [env fragment]
  (let [env
        (assoc env :AttendeeId fragment)

        user-id
        (first (d/q '[:find [?user-id ...]
                      :in $ ?attendee-id
                      :where [?attendee-id :UserId ?user-id]]
                    @api/attendees-db
                    (int fragment)))

        entity
        (when user-id (d/entity @api/users-db user-id))]
    [[(str (:FirstName entity) " " (:LastName entity))
      (routes/event-attendee env)]
     env
     nil]))

(defn event-breadcrumbs
  "Breadcrumber lambda for when we're at '/events/:id'."
  [env fragment]
  (case fragment
    "edit" [["Edit" (routes/event-edit env)] env nil]
    "register" [["Register" (routes/event-register env)] env nil]
    "activities" [["Activities" (routes/event-activities env)]
                  env
                  event-activities-breadcrumbs]
    "groups" [["Groups" (routes/event-groups env)]
              env
              event-groups-breadcrumbs]
    "attendees" [["Attendees" (routes/event-attendees env)]
                 env
                 event-attendees-breadcrumbs]
    "schedule" [["Schedule" (routes/event-schedule env)] env nil]))

(defn events-breadcrumbs
  "Breadcrumber lambda for when we're at '/events'."
  [env fragment]
  (case fragment
    "explore" [["Explore" (routes/events-explore)] env nil]
    "owned" [["Owned" (routes/events-owned)] env nil]
    "add" [["Add" (routes/event-add)] env nil]
    (let [env (assoc env :EventId fragment)]
      [[(:Name (d/entity @api/events-db (int fragment))) (routes/event env)]
       env
       event-breadcrumbs])))

(defn organization-breadcrumbs
  "Breadcrumber lambda for when we're at '/organizations/:id'."
  [env _]
  [["Edit" (routes/organization-edit env)] env nil])

(defn organizations-breadcrumbs
  "Breadcrumber lambda for when we're at '/organizations'."
  [env fragment]
  (case fragment
    "explore" [["Explore" (routes/organizations-explore)] env nil]
    "owned" [["Owned" (routes/organizations-owned)] env nil]
    "add" [["Add" (routes/organization-add)] env nil]
    (let [env (assoc env :OrganizationId fragment)]
      [[(:Name (d/entity @api/organizations-db (int fragment)))
        (routes/organization env)]
       env
       organization-breadcrumbs])))

(defn top-breadcrumbs
  "Breadcrumber lambda for when we're at '/'."
  [env fragment]
  (case fragment
    "events" [["Events" (routes/events)] env events-breadcrumbs]
    "organizations" [["Organizations" (routes/organizations)]
                     env
                     organizations-breadcrumbs]
    "calendar" [["Calendar" (routes/calendar)] env nil]
    "statistics" [["Statistics" (routes/statistics)] env nil]
    "payments" [["Payments" (routes/payments)] env nil]
    "sign-in" [["Sign in" (routes/sign-in)] env nil]
    "sign-up" [["Sign up" (routes/sign-up)] env nil]
    "password-reset" [["Password reset" (routes/password-reset)] env nil]
    "forgot-password" [["Forgot password" (routes/forgot-password)] env nil]))

(defn get-breadcrumbs
  "Breadcrumber for the initial value of a reduce over a sequence of strings."
  ([] [["Home" (routes/home)]])
  ([fragment]
   ((breadcrumber
      (fn [env _] [["Home" (routes/home)] env top-breadcrumbs]))
    []
    {}
    fragment)))

(defn component
  "Reagent component for the breadcrumbs."
  []
  (let [breadcrumbs
        ((reduce #(%1 %2)
                 get-breadcrumbs
                 (str/split (first (str/split @state/location #"\?")) #"/")))]
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
