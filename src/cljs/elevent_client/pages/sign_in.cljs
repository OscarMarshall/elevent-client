;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.sign-in
  (:require
    [reagent.core :as r :refer [atom]]
    [validateur.validation :refer [format-of presence-of validation-set]]

    [elevent-client.authentication :as auth]
    [elevent-client.routes :as routes]
    [elevent-client.components.input :as input]))

(defn page
  "Reagent component that defines the sign in page."
  [& [redirect]]
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
                                         (auth/sign-in! @form redirect)))}
           [:div.two.fields
            [:div.required.field {:class (when (and Email (:Email errors))
                                           :error)}
             [:label "Email"]
             [:div.ui.icon.input
              [input/component :email {}
               (r/wrap Email swap! form assoc :Email)]
              [:i.mail.icon]]]
            [:div.required.field {:class (when (and Password (:Password errors))
                                           :error)}
             [:label "Password"]
             [:div.ui.icon.input
              [input/component :password {}
               (r/wrap Password swap! form assoc :Password)]
              [:i.lock.icon]]]]
           [:div.field
            [:div [:a {:href (routes/forgot-password)} "Forgot password?"]]
            [:div [:a {:href (routes/sign-up)} "Don't have an account?"]]]

           [:button.ui.primary.button {:type :submit
                                       :class (when (seq errors) :disabled)}
            "Sign in"]]]]))))

(routes/register-page routes/sign-in-chan #'page)
