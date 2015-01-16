(ns util
  (:require [clojure.string :as str]))

(let [triml-with-pipe (comp #(str/replace-first % "|" "")
                            str/triml)]
  (defn ml-str*
    "Multiline string with explicit left margin."
    [s]
    (str/join "\n" (map triml-with-pipe
                        (str/split-lines s)))))

(defmacro ml-str
  "Multiline string with explicit left margin, maked by |.
Each line is trimmed of leading whitespace, up to and including
a leading | character."
  [s]
  (if (string? s)
    (ml-str* s)
    `(ml-str* ~s)))
