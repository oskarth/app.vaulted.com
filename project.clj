(defproject app "0.4.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}

  :source-paths ["src/clj" "src/cljs"]

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/clojurescript "0.0-2342"]
                 [ring "1.3.1"]
                 [cljs-ajax "0.2.6"]
                 [fogus/ring-edn "0.2.0"]       
		 [http-kit "2.1.18"]
                 [com.taoensso/sente "1.1.0"]
                 [compojure "1.1.9"]
                 [enlive "1.1.5"]
                 [figwheel "0.2.1-SNAPSHOT"]
		 [sablono "0.2.20"]
                 [secretary "1.2.1"]
                 [om "0.7.1"]
		 [prismatic/om-tools "0.3.2" :exclusions [org.clojure/clojure]]
		 [vaulted/vaulted-clj "0.1.8-SNAPSHOT"]
                 [environ "1.0.0"]
                 [com.cemerick/piggieback "0.1.3"]
                 [weasel "0.4.0-SNAPSHOT"]]

  :plugins [[lein-cljsbuild "1.0.3"]
            [lein-cljfmt "0.1.1"]
            [lein-environ "1.0.0"]]

  :aot [app.server]
  :main app.server

  :min-lein-version "2.0.0"
  :uberjar-name "app.jar"

  :cljsbuild {
    :builds {:app {:source-paths ["src/cljs"]
	            :compiler {:output-to     "resources/public/app.js"
			       :output-dir    "resources/public/out"
			       :source-map    "resources/public/out.js.map"
			       :preamble      ["react/react.min.js"]
			       :externs       ["react/externs/react.js"]
			       :optimizations :none
			       :pretty-print  true}}}}

  :profiles {
    :dev
      {:repl-options {:init-ns app.server
	              :nrepl-middleware [cemerick.piggieback/wrap-cljs-repl]}
       :plugins [[lein-figwheel "0.1.4-SNAPSHOT"]]
       :figwheel {:http-server-root "public" :port 3449}
       :env {:is-dev true}}

    :uberjar
      {:hooks [leiningen.cljsbuild]
       :env {:production true}
       :omit-source true
       :aot :all
       :cljsbuild {:builds {:app
	                     {:compiler
			       {:optimizations :advanced
			        :pretty-print false}}}}}})
