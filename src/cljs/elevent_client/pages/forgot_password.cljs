(ns elevent-client.pages.forgot-password
  (:require
    [reagent.core :as r :refer [atom]]
    [validateur.validation :refer [format-of validation-set]]
    [cljs.core.async :refer [put!]]

    [elevent-client.authentication :as auth]
    [elevent-client.routes :as routes]
    [elevent-client.state :as state]
    [elevent-client.components.input :as input]
    [elevent-client.components.action-button :as action-button]
    [elevent-client.api :as api]))

(defn page []
  (let [form (atom {})
        validator (validation-set (format-of :Email :format #"@"))]
    (fn []
      (let [{:keys [Email]} @form
            errors          (validator @form)
            request-password
            (fn [form]
              (fn [callback]
                (api/api-call :create
                              "/emails/password-reset"
                              form
                              (fn []
                                (prn "here")
                                (callback)
                                (put! state/add-messages-chan
                                      [:password-reset-request-succeeded
                                       [:positive "An link to reset your password has been sent to your email"]])
                                (js/location.replace (routes/sign-in))))))]
        [:div.eight.wide.centered.column
         [:div.ui.segment
          [:h1.ui.dividing.header "Request password reset"]
          [:p "Enter your email address to request a password reset."]
          [:form.ui.form {:on-submit (fn [e]
                                       (.preventDefault e)
                                       (when (empty? errors)
                                         (request-password @form)))}
           [:div.one.field
            [:div.required.field {:class (when (and Email (:Email errors))
                                           :error)}
             [:label "Email"]
             [:div.ui.icon.input
              [input/component :email {}
               (r/wrap Email swap! form assoc :Email)]
              [:i.mail.icon]]]]
           [action-button/component
             {:class [:primary (when (seq errors) :disabled)]}
             "Send email"
             (request-password @form)]]]]))))

(routes/register-page routes/forgot-password-chan #'page)
