(ns elevent-client.pages.sign-up
  (:require [reagent.core :as r :refer [atom]]
            [validateur.validation :refer [format-of
                                           inclusion-of
                                           length-of
                                           presence-of
                                           validation-set]]

            [elevent-client.authentication :as auth]
            [elevent-client.routes :as routes]
            [elevent-client.components.input :as input]))

(defn page []
  (let [form (atom {})]
    (fn []
      (let [{:keys [Email Password PasswordConfirm FirstName LastName]} @form
            validator (validation-set (format-of :Email :format #"@")
                                      (presence-of :Password)
                                      (inclusion-of :PasswordConfirm
                                                    :in #{(:Password @form)})
                                      (presence-of :FirstName)
                                      (presence-of :LastName)
                                      (length-of :Password
                                                 :within (range 8 100)))
            errors                   (validator @form)]
        [:div.eight.wide.centered.column
         [:div.ui.segment
          [:h1.ui.dividing.header "Sign Up"]
          [:form.ui.form {:on-submit (fn [e]
                                       (.preventDefault e)
                                       (when (empty? errors)
                                         (auth/sign-up! @form)))}
           [:div.required.field {:class (when (and Email (:Email errors))
                                          :error)}
            [:label "Email"]
            [:div.ui.icon.input
             [input/component :email {}
              (r/wrap Email swap! form assoc :Email)]
             [:i.mail.icon]]]
           [:div.two.fields
            [:div.required.field {:class (when (and Password (:Password errors))
                                           :error)}
             [:label "Password"]
             [:div.ui.icon.input
              [input/component :password {}
               (r/wrap Password swap! form assoc :Password)]
              [:i.lock.icon]]]
            [:div.required.field {:class (when (and PasswordConfirm
                                                    (:PasswordConfirm errors))
                                           :error)}
             [:label "Confirm Password"]
             [:div.ui.input
              [input/component :password {}
               (r/wrap PasswordConfirm swap! form assoc :PasswordConfirm)]]]]
           [:div.two.fields
            [:div.required.field {:class (when (and FirstName
                                                    (:FirstName errors))
                                           :error)}
             [:label "First Name"]
             [:div.ui.input
              [input/component :text {}
               (r/wrap FirstName swap! form assoc :FirstName)]]]
            [:div.required.field {:class (when (and LastName
                                                    (:LastName errors))
                                           :error)}
             [:label "Last Name"]
             [:div.ui.input
              [input/component :text {}
               (r/wrap LastName swap! form assoc :LastName)]]]]
           [:button.ui.primary.button {:type :submit
                                       :class (when (seq errors) :disabled)}
            "Sign up"]]]]))))

(routes/register-page routes/sign-up-chan #'page)
