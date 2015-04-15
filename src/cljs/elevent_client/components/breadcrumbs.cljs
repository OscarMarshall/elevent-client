(ns elevent-client.components.breadcrumbs
  (:require [clojure.string :as str]

            [datascript :as d]

            [elevent-client.api :as api]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]))

(defn component []
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
          [["Edit" (routes/event-activity-edit env)] env nil])

        event-activities-breadcrumbs
        (fn [env fragment]
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

        event-attendees-breadcrumbs
        (fn [env fragment]
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

        event-breadcrumbs
        (fn [env fragment]
          (case fragment
            "edit" [["Edit" (routes/event-edit env)] env nil]
            "register" [["Register" (routes/event-register env)] env nil]
            "activities" [["Activities" (routes/event-activities env)]
                          env
                          event-activities-breadcrumbs]
            "attendees" [["Attendees" (routes/event-attendees env)]
                         env
                         event-attendees-breadcrumbs]
            "schedule" [["Schedule" (routes/event-schedule env)] env nil]))

        events-breadcrumbs
        (fn [env fragment]
          (case fragment
            "explore" [["Explore" (routes/events-explore)] env nil]
            "owned" [["Owned" (routes/events-owned)] env nil]
            "add" [["Add" (routes/event-add)] env nil]
            (let [env (assoc env :EventId fragment)]
              [[(:Name (d/entity @api/events-db (int fragment))) (routes/event env)]
               env
               event-breadcrumbs])))

        organization-breadcrumbs
        (fn [env _]
          [["Edit" (routes/organization-edit env)] env nil])

        organizations-breadcrumbs
        (fn [env fragment]
          (case fragment
            "explore" [["Explore" (routes/organizations-explore)] env nil]
            "add" [["Add" (routes/organization-add)] env nil]
            (let [env (assoc env :OrganizationId fragment)]
              [[(:Name (d/entity @api/organizations-db (int fragment)))
                (routes/organization env)]
               env
               organization-breadcrumbs])))

        top-breadcrumbs
        (fn [env fragment]
          (case fragment
            "events" [["Events" (routes/events)] env events-breadcrumbs]
            "organizations" [["Organizations" (routes/organization)]
                             env
                             organizations-breadcrumbs]
            "calendar" [["Calendar" (routes/calendar)] env nil]
            "statistics" [["Statistics" (routes/statistics)] env nil]
            "payments" [["Payments" (routes/payments)] env nil]
            "sign-in" [["Sign in" (routes/sign-in)] env nil]
            "sign-up" [["Sign up" (routes/sign-up)] env nil]
            "password-reset" [["Password reset" (routes/password-reset)] env nil]
            "forgot-password" [["Forgot password" (routes/forgot-password)] env nil]))

        get-breadcrumbs
        (fn
          ([] [["Home" (routes/home)]])
          ([fragment]
           ((breadcrumber
              (fn [env _] [["Home" (routes/home)] env top-breadcrumbs]))
            []
            {}
            fragment)))

        breadcrumbs
        ((reduce #(%1 %2) get-breadcrumbs (str/split (first (str/split @state/location #"\?")) #"/")))]
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
