;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.password-reset
  (:require [reagent.core :as r :refer [atom]]
            [cljs.core.async :refer [put!]]
            [validateur.validation :refer [inclusion-of
                                           length-of
                                           presence-of
                                           validation-set]]

            [elevent-client.authentication :as auth]
            [elevent-client.routes :as routes]
            [elevent-client.state :as state]
            [elevent-client.components.input :as input]
            [elevent-client.components.action-button :as action-button]
            [elevent-client.api :as api]))

(defn page
  "Reagent component that defines the password reset page."
  [token]
  (let [form (atom {})]
    (fn []
      (let [{:keys [Password PasswordConfirm]} @form
            validator (validation-set (presence-of :Password)
                                      (inclusion-of :PasswordConfirm
                                                    :in #{(:Password @form)})
                                      (length-of :Password
                                                 :within (range 8 100)))
            errors                   (validator @form)
            reset-password
            (fn [form]
              (fn [callback]
                (api/api-call :update
                              "/users/password"
                              (assoc form :Token token)
                              (fn []
                                (callback)
                                (reset! state/messages {})
                                (put! state/add-messages-chan
                                      [:password-reset-succeeded
                                       [:positive "You have successfully changed your password"]])
                                (js/location.replace (routes/sign-in)))
                              (fn []
                                (callback)
                                (reset! state/messages {})
                                (put! state/add-messages-chan
                                      [:password-reset-failed
                                       [:negative "Request failed. Please try again."]])))))]
        [:div.eight.wide.centered.column
         [:div.ui.segment
          [:h1.ui.dividing.header "Password Reset"]
          [:form.ui.form {:on-submit (fn [e]
                                       (.preventDefault e)
                                       (when (empty? errors)
                                         (reset-password @form)))}
           [:div.two.fields
            [:div.required.field {:class (when (and Password (:Password errors))
                                           :error)}
             [:label "New Password"]
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
           [action-button/component
             {:class [:primary (when (seq errors) :disabled)]}
             "Confirm"
             (reset-password @form)]]]]))))

(routes/register-page routes/password-reset-chan #'page)
