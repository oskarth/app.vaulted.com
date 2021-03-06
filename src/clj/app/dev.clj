(ns app.dev
  (:require [environ.core :refer [env]]
            [net.cgrand.enlive-html :refer [set-attr prepend append html]]
            [cemerick.piggieback :as piggieback]
            [weasel.repl.websocket :as weasel]))

(def is-dev? (env :is-dev))

(def inject-devmode-html
  (comp
   (set-attr :class "is-dev")
   (prepend (html [:script {:type "text/javascript" :src "/out/goog/base.js"}]))
   (prepend (html [:script {:type "text/javascript" :src "/react/react.js"}]))
   (append  (html [:script {:type "text/javascript"} "goog.require('app.core')"]))))

(defn browser-repl []
  (piggieback/cljs-repl :repl-env (weasel/repl-env :ip "0.0.0.0" :port 9001)))
