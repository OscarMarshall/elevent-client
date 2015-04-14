(ns elevent-client.components.date-selector
  (:require [reagent.core :as r]
            [cljs-time.core :refer [after? hours minus plus]]
            [cljs-time.coerce :refer [from-string to-string from-date to-date]]
            [cljs-time.format :refer [formatters unparse parse]]

            [elevent-client.locale :as locale]))

;; Modified version of Tim Gilbert's cljs-pikaday
;; Copyright (c) 2012 Tim Gilbert
;; See third-party/cljs-pikaday-license.txt
(defn component
  "Return a date-selector reagent component. Takes a single map as its argument,
  with the following keys: date-atom: an atom or reaction bound to the date
  value represented by the picker.
  max-date-atom: atom representing the maximum date for the selector.
  min-date-atom: atom representing the minimum date for the selector.
  pikaday-attrs: a map of options to be passed to the Pikaday constructor.
  input-attrs: a map of options to be used as <input> tag attributes."
  [{:keys [date-atom max-date-atom min-date-atom pikaday-attrs input-attrs
           static-attrs]}]
  (let [in
        #(if (from-string %)
           (unparse locale/input-date-time-formatter (from-string %))
           %)
        out
        #(if (from-string %)
           (unparse (:date-hour-minute formatters) (from-string %))
           %)
        from-input-string
        #(let [in-date
               (try (parse locale/input-date-time-formatter %)
                 (catch :default _))]
           (if in-date
             (unparse (:date-hour-minute formatters)
                      (parse locale/input-date-time-formatter %))
             %))
        normalize ; because of GMT
        #(to-date (plus (from-string %) (hours 6)))
        min-date (when (:min-date static-attrs) (:min-date static-attrs))
        max-date (when (:max-date static-attrs) (:max-date static-attrs))
        set-date! (fn [new-date]
                    (when (and date-atom new-date)
                      (if (and min-date max-date)
                        (if (after? (from-date new-date) max-date)
                          (reset! date-atom (out (to-string max-date)))
                          (if (after? min-date (from-date new-date))
                            (reset! date-atom (out (to-string min-date)))
                            (reset! date-atom
                                    (out (to-string (minus (from-date new-date)
                                                           (hours 6)))))))
                        (reset! date-atom
                                (out (to-string (minus (from-date new-date)
                                                       (hours 6))))))))]
    (r/create-class
      {:component-did-mount
        (fn [this]
          (let [default-opts
                {:field (.getDOMNode this)
                 :defaultDate @date-atom
                 :setDefaultDate true
                 :onSelect set-date!}
                opts (merge default-opts pikaday-attrs)
                instance (js/Pikaday. (clj->js opts))]
            (when (and (:defaultDate opts)
                       (:setDefaultDate opts))
              (when (from-date (:defaultDate opts))
                (-> this
                    r/dom-node
                    js/jQuery
                    (.val (in (to-string (minus (from-date (:defaultDate opts))
                                                (hours 6))))))))
            ; This code could probably be neater
            (when date-atom
              (add-watch date-atom :update-instance
                (fn [ke ref old new]
                  ;; final parameter here causes pikaday to skip onSelect()
                  ;; callback
                  (when (from-string new)
                    (.setDate instance (normalize new) true)))))
            (when min-date-atom
              (add-watch min-date-atom :update-min-date
                (fn [key ref old new]
                  (when (from-string new)
                    (.setMinDate instance (normalize new))
                    ;; If new min date is greater than selected date, reset
                    ;; actual date to min
                    (if (< @date-atom new)
                      (reset! date-atom new))))))
            (when max-date-atom
              (add-watch max-date-atom :update-max-date
                (fn [key ref old new]
                  (when (from-string new)
                    (.setMaxDate instance (normalize new))
                    ;; If new max date is less than selected date, reset actual
                    ;; date to max
                    (if (> @date-atom new)
                      (reset! date-atom new))))))))
        :component-did-update
        (fn [this]
          (let [value (-> this
                          r/dom-node
                          js/jQuery
                          .val)]
            (when (from-string value)
              (-> this
                  r/dom-node
                  js/jQuery
                  (.val (in @date-atom))))))
        :reagent-render
        (fn [input-attrs]
          [:input (assoc input-attrs
                    :on-change #(reset! date-atom (-> %
                                                      .-target
                                                      .-value
                                                      from-input-string)))])})))
