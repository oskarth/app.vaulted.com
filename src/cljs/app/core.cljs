(ns app.core
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [app.controllers :as controllers]
            [app.components :as components]
            [app.state :as state]
            [om.core :as om :include-macros true]
            [cljs.core.async :as async
             :refer (<! >! put! chan sliding-buffer timeout)]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(om/root components/app-view state/app-state
         {:target (. js/document (getElementById "app"))})
