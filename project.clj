(defproject elevent-client "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-3208" :scope "provided"]
                 [org.clojure/core.async "0.1.346.0-17112a-alpha"]
                 [cljsjs/react "0.13.1-0"]
                 [ring "1.3.2"]
                 [ring/ring-defaults "0.1.4"]
                 [reagent "0.5.0"]
                 [alandipert/storage-atom "1.2.4"]
                 [cljs-ajax "0.3.11"]
                 [com.andrewmcveigh/cljs-time "0.3.3"]
                 [com.novemberain/validateur "2.4.2"]
                 [compojure "1.3.3"]
                 [datascript "0.10.0"]
                 [environ "1.0.0"]
                 [garden "1.2.5"]
                 [hiccup "1.0.5"]
                 [prone "0.8.1"]
                 [secretary "2.0.0.1-260a59"]]

  :plugins [[lein-cljsbuild "1.0.5"]
            [lein-environ "1.0.0"]
            [lein-ring "0.9.3"]
            [lein-asset-minifier "0.2.2"]]

  :ring {:handler elevent-client.handler/app
         :uberwar-name "elevent-client.war"}

  :min-lein-version "2.5.0"

  :uberjar-name "elevent-client.jar"

  :main elevent-client.server

  :clean-targets ^{:protect false} ["resources/public/js"]

  :minify-assets
  {:assets {}}

  :cljsbuild {:builds {:app {:source-paths ["src/cljs"]
                             :compiler {:output-to     "resources/public/js/app.js"
                                        :output-dir    "resources/public/js/out"
                                        ;;:externs       ["react/externs/react.js"]
                                        :asset-path   "js/out"
                                        :optimizations :none
                                        :pretty-print  true}}
                       :prod {:source-paths ["env/prod/cljs"]
                              :compiler
                              {:output-to     "resources/public/js/app.js"
                               :optimizations :whitespace
                               :pretty-print  false}}}}

  :profiles {:dev {:repl-options {:init-ns elevent-client.dev
                                  :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}

                   :dependencies [[ring-mock "0.1.5"]
                                  [ring/ring-devel "1.3.2"]
                                  [leiningen "2.5.1"]
                                  [figwheel "0.2.5"]
                                  [weasel "0.6.0"]
                                  [com.cemerick/piggieback "0.1.6-SNAPSHOT"]
                                  [pjstadig/humane-test-output "0.7.0"]]

                   :source-paths ["env/dev/clj"]
                   :plugins [[lein-figwheel "0.2.5"]]

                   :injections [(require 'pjstadig.humane-test-output)
                                (pjstadig.humane-test-output/activate!)]

                   :figwheel {:http-server-root "public"
                              :server-port 8000
                              :ring-handler elevent-client.handler/app}

                   :env {:dev? true}

                   :cljsbuild {:builds {:app {:source-paths ["env/dev/cljs"]
                                              :compiler {   :main "elevent-client.dev"
                                                         :source-map true}}}}}

             :uberjar {:hooks [leiningen.cljsbuild minify-assets.plugin/hooks]
                       :env {:production true}
                       :aot :all
                       :omit-source true
                       :cljsbuild {:jar true
                                   :builds {:app
                                             {:source-paths ["env/prod/cljs"]
                                              :compiler
                                              {:optimizations :advanced
                                               :pretty-print false}}}}}})
