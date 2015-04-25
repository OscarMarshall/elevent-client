;; Senior Project 2015
;; Elevent Solutions -- Client
;; Leslie Baker and Oscar Marshall

(ns elevent-client.pages.payments
  (:require [elevent-client.state :as state]
            [elevent-client.components.payments :as payments]))

(defn payments-page
  "Reagent component that defines the payments page."
  []
  [:div.sixteen.wide.column
   [:div.ui.segment
    ; TODO: expire stripe token if payment info changes
    [:form#payments-form.ui.form
     [payments/component]]]])
