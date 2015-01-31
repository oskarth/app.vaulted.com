(ns app.controllers
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [app.state :as state]
            [om.core :as om :include-macros true]
            [cljs.core.async :as async
             :refer (<! >! put! chan sliding-buffer timeout alts!)]
            [cljs.reader :as reader]
            [ajax.core :refer (GET PUT POST)]
            [taoensso.sente  :as sente :refer (cb-success?)]))

(defn base-url [] @state/base-url)

;; global extra channel
 (def comm-alt (chan (sliding-buffer 1)))

;; Utils

(defn map-fn-vals [m f]
  (into {} (for [[k v] m] [k (f v)])))

(defn transform-params
  "{:key [:user :key] :user [:user :id]} => {:key foo :id :bar},
  with help of app-state."
  [app params]
  (map-fn-vals params #(get-in app %)))

;; Helpers

(defn hide! [app key bool]
  (om/transact! app :hidden (fn [m] (assoc m key bool))))

(defn hide-all! [app]
  (let [new (map-fn-vals (:hidden @app) (fn [_] true))]
    (om/transact! app :hidden (fn [_] new))))

(defn start-progress! [app msg]
  (hide! app :error true)
  (hide! app :progress false)
  (om/transact! app :progress (fn [_] {:message msg})))

(defn view-error! [app msg]
  (hide! app :error false)
  (om/transact! app :error (fn [_] {:message msg})))

(defn default-err-fn [app msg]
  (view-error! app msg))

(defn put-merchant-err-fn [app msg]
  (if (= (-> @app :user :mode) :live)
    (view-error!
     app
     "Updating account details is currently not enabled for live accounts.")
    (default-err-fn app msg)))

(defn put-merchant-ok! [app resp]
  (om/transact! app :user (fn [_] resp))
  ;; just need a few render frames to hack around normal progress-hide.
  (go (<! (timeout 50))
      (start-progress! app
                       (str "Updated merchant details!"))))

(defn auth-ok! [app resp]
  (om/transact! app :user (fn [_] resp))
  (om/transact! app [:tour :email] (fn [_] (:contact_email resp)))
  (hide-all! app)
  (if (= (:mode resp) :live)
    (hide! app :welcome false)
    (hide! app :tour/first false)))

(defn go-tour-first! [app resp]
  (hide-all! app)
  (hide! app :tour/first false))

(defn get-merchant-ok! [app resp]
  (om/transact! app :user (fn [_] resp))
  (hide-all! app)
  (hide! app :user false))

(defn get-statement-ok! [app message]
  (hide-all! app)
  (hide! app :statements false)
  (om/transact! app :statements (fn [_] message)))

;; TODO
(defn get-customers-ok! [app message]
  (hide-all! app)
  (hide! app :customer false)
  (om/transact! app :customers (fn [_] (:users (:answer message)))))

(defn tour-get-statement-ok! [app message]
  (om/transact! app :statements (fn [_] message))
  (hide-all! app)
  (hide! app :tour/statements false))

(defn get-statement-tour-ok! [app message]
  (om/transact! app :statements (fn [_] message))
  (hide-all! app)
  (hide! app :tour/cta false))

(defn get-statement-csv-ok! [app _]
  (let [id (get-in @app [:user :id])]
    (set! (.-location js/window)
          (str (base-url) "/csv/" id))))

;; WARNING: Overloaded, used both standalone and in tour.
 (defn post-customer-ok! [app resp]
   ;; interfers with tour?
  ;; just need a few render frames to hack around normal progress-hide.
  (go (<! (timeout 50))
      (start-progress! app
                       (str "Customer created! Saved in cache.")))

   (om/transact! app :customer (fn [_] resp))
   (om/transact! app [:tour :cid] (fn [_] (:id resp)))) ;; not always true...

(defn go-home! [app]
  (hide-all! app)
  (if (-> @app :user :key)
    (hide! app :welcome false)
    (hide! app :auth false)))

(defn go-technical! [app]
  (hide-all! app)
  (hide! app :account/technical false))

(defn go-customer! [app]
  (hide-all! app)
  (hide! app :customer false))

(defn clear-customer! [app]
  (hide-all! app)
  (om/transact! app :customer (fn [_] {}))
  ;; reload..., not really working for populated text fields though :(
  (hide! app :customer false))

(defn go-debit! [app]
  (hide-all! app)
  (hide! app :debit false))

(defn go-tour-cta-view! [app resp]
  (hide-all! app)
  (hide! app :tour/cta false))

;; XXX: reverse natural order
 (def tx-names-map
   {"Created at" :created_at
    "Reference"  :reference
    "Amount"     :amount
    "Instrument" :instrument
    "Customer"   :customer
    "Payment ID" :payment_id
    "Status"     :status})

(defn sort-transactions!
  "Sorts nested transaction by its header name."
  [app val]
  (om/transact! app
                [:statements :history]
                (fn [hists]
                  (sort-by #(get-in % [:history (get tx-names-map (:by val))])
                           hists))))

(defn post-customer-ok-post-debit! [app resp]
  (om/transact! app [:tour :cid] (fn [_] (:id resp)))  
  (put! comm-alt
        {:tag :tour/post-debit
         :value {:token (-> @app :user :key)
                 :cid (-> @app :tour :cid)
                 :number "0000001"
                 :items [{:amount "999"
                          :description "Box of delicious candy!"}]}}))

(defn post-debit-tour-ok! [app resp]
  (go (<! (timeout 50))
      (start-progress!
       app
       (str "Invoice sent! Check your inbox or click next to see the status of your debit."))))

(defn post-debit-ok! [app resp]
  (go (<! (timeout 50))
      (start-progress!
       app
       (str "Invoice sent!"))))

;; TODO: :other -> :render with :views and :action for side-effects?
 (def ops-table
   {:go-home {:type :other
              :method go-home!}
    :go-customer {:type :other
                  :method go-customer!}
    :go-debit {:type :other
               :method go-debit!}
    :go-technical {:type :other
                   :method go-technical!}
    :tour/go-first {:type :other
                    :method go-tour-first!}
    :tour/go-cta-view {:type :other
                       :method go-tour-cta-view!}
    :tour/post-debit {:type :ajax
                      :method POST
                      :uri-fn #(str (base-url) "/debit")
                      :progress-msg "Posting debit..."
                      :ok-fn post-debit-tour-ok!
                      :err-fn default-err-fn}
    :sort/transactions {:type :other
                        :method sort-transactions!}
    :clear-customer {:type :other
                     :method clear-customer!}
    :auth {:type :ajax
           :method GET
           :uri-fn #(str (base-url) "/auth/")
           :progress-msg "Authenticating..."
           :ok-fn auth-ok!
           :err-fn default-err-fn}
    :get-customers {:type :ajax
                    :method GET
                    :uri-fn #(str (base-url) "/filter/customer")
                    :progress-msg "Getting customers..."
                    :ok-fn get-customers-ok!
                    :err-fn default-err-fn}
     :get-statement {:type :ajax
                    :method GET
                    :uri-fn #(str (base-url) "/merchant-statement/")
                    :progress-msg "Fetching transactions..."
                    :ok-fn get-statement-ok!
                    :err-fn default-err-fn}
    :tour/get-statement {:type :ajax
                         :method GET
                         :uri-fn #(str (base-url) "/merchant-statement/")
                         :progress-msg "Fetching transactions..."
                         :ok-fn tour-get-statement-ok!
                         :err-fn default-err-fn}
    :get-statement-csv {:type :ajax
                        :method GET
                        :uri-fn #(str (base-url) "/merchant-statement-csv/")
                        :progress-msg "Fetching transactions as CSV..."
                        :ok-fn get-statement-csv-ok!
                        :err-fn default-err-fn}
    :get-merchant {:type :ajax
                   :method GET
                   :uri-fn #(str (base-url) "/merchant/")
                   :progress-msg "Getting account data..."
                   :ok-fn get-merchant-ok!
                   :err-fn default-err-fn}
    :put-merchant {:type :ajax
                   :method PUT
                   :uri-fn #(str (base-url) "/merchant/")
                   :progress-msg "Updating account data..."
                   :ok-fn put-merchant-ok!
                   :err-fn put-merchant-err-fn}

    ;; untested, unused on its own atm
   :post-customer {:type :ajax
                   :method POST
                   :uri-fn #(str (base-url) "/customers")
                   :progress-msg "Creating customer..."
                   :ok-fn post-customer-ok!
                   :err-fn default-err-fn}

    ;; same as post-customer, except in ok-fn we put stuff on post debit chan.
   :send-invoice  {:type :ajax
                   :method POST
                   :uri-fn #(str (base-url) "/customers")
                   :progress-msg "Creating customer to send invoice..."
                   :ok-fn post-customer-ok-post-debit!
                   :err-fn default-err-fn}
    :post-debit {:type :ajax
                 :method POST
                 :uri-fn #(str (base-url) "/debit")
                 :progress-msg "Posting debit..."
                 :ok-fn post-debit-ok!
                 :err-fn default-err-fn}})

(defn event-loop
  "When events come in, look up operation to perform and what to do
  if ok/err in action tree."
  [app owner event-chan]
  (go
    (while true
      (let [[{:keys [tag value]} _] (alts! [comm-alt event-chan])
            {:keys [type method uri-fn progress-msg ok-fn err-fn] :as ops}
            (get ops-table tag)]
        (. js/console (log "event tag: " (pr-str tag)))
        (. js/console (log "event val: " (pr-str value)))
        (condp keyword-identical? type
          :ajax
          (do
            (start-progress! app progress-msg)
            (method (uri-fn)
                    {:params value
                     :handler
                     (fn [body]
                       (let [{:keys [status message]} (reader/read-string body)]
                         (if (= status "ok")
                           (ok-fn app message)
                           (err-fn app message))
                         (hide! app :progress true)))
                     :timeout 20000
                     :error-handler
                     (fn [err] (prn (str "error: " err)))}))
          :other
          (if value
            (method app value)
            (method app)) ;; (do  (if value
 ;;        (do (. js/console (log (str "other val" value)))
 ;;            (method app value))
 ;;        (do (. js/console (log (str "no val")))
 ;;            (method app))))
)))))
