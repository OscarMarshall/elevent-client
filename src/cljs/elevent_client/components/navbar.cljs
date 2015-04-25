(ns elevent-client.components.navbar
  (:require [elevent-client.state :as state]
            [elevent-client.routes :as routes]
            [elevent-client.authentication :as auth]))

(defn component []
  "Persistent navbar"
  (let [logged-in? (:token @state/session)]
    [:nav.ui.fixed.menu
     [:a.logo.item {:href (routes/home)}
      [:img {:src "images/logo-menu.png"}]]
     [:a.blue.item
      {:href (if logged-in? (routes/events) (routes/events-explore))
       :class (when (re-find (re-pattern (routes/events)) @state/location)
                :active)}
      [:i.ticket.icon]
      "Events"]
     [:a.blue.item
      {:href (if logged-in?
               (routes/organizations)
               (routes/organizations-explore))
       :class (when (re-find (re-pattern (routes/organizations)) @state/location)
                :active)}
      [:i.building.icon]
      "Organizations"]
     (when logged-in?
       [:a.blue.item
        {:href (routes/calendar)
         :class (when (re-find (re-pattern (routes/calendar)) @state/location)
                  :active)}
        [:i.calendar.icon]
        "Calendar"])
     (when logged-in?
       [:a.blue.item
        {:href (routes/statistics)
         :class (when (re-find (re-pattern (routes/statistics)) @state/location)
                  :active)}
        [:i.bar.chart.icon]
        "Statistics"])
     ; Check if logged in
     (if (:token @state/session)
       [:div.right.menu
        [:div.item
         [:i.user.icon]
         (:FirstName (:user @state/session))]
        [:a.blue.item {:on-click auth/sign-out!}
         [:i.sign.out.icon]
         "Sign out"]]
       [:div.right.menu
        [:a.blue.item
         {:href (routes/sign-in)
          :class (when (re-find (re-pattern (routes/sign-in)) @state/location)
                   :active)}
         [:i.sign.in.icon]
         "Sign in"]
        [:a.blue.item
         {:href (routes/sign-up)
          :class (when (re-find (re-pattern (routes/sign-up)) @state/location)
                   :active)}
         [:i.add.user.icon]
         "Sign up"]])]))
