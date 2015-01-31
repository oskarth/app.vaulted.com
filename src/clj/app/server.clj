(ns app.server
  (:gen-class)
  (:use     [ring.middleware.edn]) ;; wrap-edn-params
    (:require [clojure.java.io :as io]
              [clojure.core.async :as async
               :refer (<! <!! >! >!! put! chan go go-loop)]
              [app.dev :refer [is-dev? inject-devmode-html browser-repl]]
              [org.httpkit.server :refer (run-server)]
              [taoensso.sente :as sente]
              [vaulted-clj.core :as vaulted]
              [slingshot.slingshot :refer [try+ throw+]]
              [compojure.core :refer [GET POST PUT defroutes]]
              ;;[compojure.core :refer :all]
              [compojure.route :as route :refer [resources]]
              [compojure.handler :as handler]
              [ring.util.response :as response]
              [net.cgrand.enlive-html :refer [deftemplate]]
              [ring.middleware.reload :as reload]
              [ring.middleware.keyword-params :refer [wrap-keyword-params]]
              [ring.middleware.nested-params :refer [wrap-nested-params]]
              [ring.middleware.session :refer [wrap-session]]
              [ring.middleware.params :refer [wrap-params]]
              [environ.core :refer [env]]
              [taoensso.sente :as sente]))

(defn get-key [mode]
  (if (= mode :live)
    (env :www-live-key)
    (env :www-test-key)))

(defn log [msg arg]
  (println msg ": " arg)
  arg)

;; temporary map for CSV data
 (def csv-map (atom {}))

;; body-transforms == inject-dev-mode-html?
 (deftemplate page
              (io/resource "index.html") [] [:body]
              (if is-dev? inject-devmode-html identity))

(defn wrap-errors [fn params]
  (try+ (fn params)
        (catch [:status 400] {:keys [message]}
          (str {:status "error" :message message}))
        (catch [:status 401] {:keys [message]}
          (str {:status "error" :message message}))
        (catch [:status 404] {:keys [message]}
          (str {:status "error" :message message}))
        (catch [:status 500] {:keys [message]}
          (str {:status "error" :message message}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
 ;; API calls

(def filter-customers-url "https://test.vaulted.com/v0/filter/customers")

(defn auth [key]
  (try+
   (let [resp (vaulted/with-token key
                                  (vaulted/auth key :test))]
     (log "auth" (str {:status "ok" :message  (assoc resp :mode :test)})))
   (catch [:status 401] _
     (try+
      (let [resp (vaulted/with-token key
                                     (vaulted/auth key :live))]
        (log "auth" (str {:status "ok" :message (assoc resp :mode :live)})))))))

;; XXX: Url hard-coded for test. Not public API support at time of writing.
(defn get-filter-customers [params]
   (let [mode (keyword (:mode params))
         resp (vaulted/with-token (:token params)
                   		  (vaulted/get-resource filter-customers-url))]
     (log "get-filter-customers"
          (str {:status "ok" :message (assoc resp :mode mode)}))))

;; why isn't this id?
 (defn get-merchant [params]
   (let [mode (keyword (:mode params))
         resp (vaulted/with-token (get-key mode)
                                  (vaulted/get-merchant (:user params) mode))]
     (log "get-merchant"
          (str {:status "ok" :message (assoc resp :mode mode)}))))

(defn put-merchant [params]
  (let [mode (keyword (:mode params))
        merchant (dissoc params :mode)
        resp (vaulted/with-token (get-key mode)
                                 (vaulted/put-merchant (:id merchant) merchant mode))]
    (log "put-merchant: "
         (str {:status "ok" :message (assoc resp :mode mode)}))))

;; dissoc should be in vaulted-clj
 (defn post-customer [params]
   (let [mode (keyword (:mode params))
         customer (dissoc params :token :refunds_uri :mode
                          :credits_uri :debits_uri :instruments
                          :uri :created_at :id :_type)
         resp (vaulted/with-token (:token params)
                                  (vaulted/post-customer customer mode))]
     (log "post-customer: " (str {:status "ok" :message resp}))))

;; mode in ednparams?
 (defn post-debit [ednparams]
   (let [mode (keyword (:mode ednparams))
         debit (dissoc ednparams :token :cid :temp-item :mode)
         resp (vaulted/with-token (:token ednparams)
                                  (vaulted/post-debit (:cid ednparams) debit mode))]
     (log "post-debit: " (str {:status "ok" :message resp}))))

(defn get-statement [params]
  (let [mode (keyword (:mode params))
        resp (vaulted/with-token (:key params)
                                 (vaulted/get-merchant-statement (:id params) mode))]
    (log "get-statement" (str {:status "ok" :message resp}))))

(defn get-statement-csv
  "Puts it in atom so it can be accessed with one parameter"
  [params]
  (let [mode (keyword (:mode params))
        resp (vaulted/with-token (:key params)
                                 (vaulted/get-merchant-statement-csv (:id params) mode))]
    (swap! csv-map assoc (:id params) (:csv resp))
    (log "get-statement-csv" (str {:status "ok"
                                   :message "CSV statement accessible. "}))))

(defn get-statement-csv-file
  "Actually gets the file. Only present after get-statement-csv swap!"
  [id]
  (-> (response/response (get @csv-map (str id)))
      (response/header "Content-Type" "text/csv; charset=utf-8")
      (response/header
       "Content-Disposition" "attachment; filename=statement.csv")))

(defroutes routes
  (GET "/" [] (page))
  ;;(POST "/login" req (login! req))

  (GET "/auth/" {params :params} (wrap-errors auth (:key params)))
  (POST "/customers" {params :params} (wrap-errors post-customer params))
  (POST "/debit" {ednparams :edn-params} (wrap-errors post-debit ednparams))
  (GET "/merchant/" {params :params} (wrap-errors get-merchant params))
  (PUT "/merchant/" {params :params} (wrap-errors put-merchant params))
  (GET "/merchant-statement/" {params :params} (wrap-errors get-statement params))
  (GET "/merchant-statement-csv/" {params :params} (wrap-errors get-statement-csv params))
  
  (GET "/csv/:id" [id]  (get-statement-csv-file id))
  (GET "/filter/customer" {params :params} (wrap-errors get-filter-customers params))

  (resources "/react" {:root "react"})
  (resources "/")
  (route/not-found "Not Found"))

(defn api [routes]
  (-> routes
      wrap-keyword-params
      wrap-nested-params
      wrap-params
      wrap-edn-params))

;; what is api exactly
 (def http-handler
   (if is-dev?
     (reload/wrap-reload (api #'routes))
     (api routes)))

(defn run [& [port]]
  (defonce ^:private server
           (run-server #'http-handler {:port (Integer. (or port (env :port) 9500))
                                       :join? false}))
  server)

(defn -main [& [port]]
  (run port))
