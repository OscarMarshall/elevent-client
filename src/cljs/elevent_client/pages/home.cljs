(ns elevent-client.pages.home
  (:require [elevent-client.routes :as routes]))

(defn page []
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

(routes/register-page routes/home-chan #'page)
