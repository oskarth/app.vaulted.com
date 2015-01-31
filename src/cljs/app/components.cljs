(ns app.components
  (:require-macros [cljs.core.async.macros :as asyncm :refer (go go-loop)])
  (:require [app.controllers :as controllers]
            [app.state :as state]
            [app.dev :refer [is-dev?]]
            [om.core :as om :include-macros true]
            [cljs.core.async :as async :refer (put! chan sliding-buffer)]
            [cljs.reader :as reader]
            [sablono.core :as html :refer-macros [html]]
            [ajax.core :refer (GET PUT POST)]))

;; UTILS / HELPERS

(defn get-query-key [url]
  (second (re-find #"auth=(.*)" url)))

(defn hidden [^boolean bool]
  (if bool
    #js {:display "none"}
    #js {:display "block"}))

(defn input-box-input [owner chan]
  [:div {:class "form-group"}
   [:input {:type "text"
            :class "form-control"
            :on-change
            (fn [e]
              (om/set-state! owner :value (.-value (.-target e))))}]])

(defn input-box-button [owner chan]
  [:button {:type "button"
            :class "btn btn-primary"
            :on-click
            (fn [e]
              (let [value (om/get-state owner :value)]
                (when value
                  (put! chan {:tag :auth :value {:key value}}))))}
   "Auth"])

(defn gen-link [name chan msg class]
  [:a {:class class :href "#" :on-click (fn [e] (put! chan msg))} name])

(defn gen-button [name class chan msg]
  [:a {:type "button"
       :href "#"
       :class (str "btn " class)
       :on-click (fn [e] (put! chan msg))}
   name])

(defn reactive-textarea [cursor val key]
  [:textarea {:type "text" :value val
              :rows "5" :cols "80"
              :onChange
              #(om/transact! cursor key (fn [_] (.. % -target -value)))}])

;; Added form-control. Does this fuck up edit view?
 ;; A bit perhaps, but better I think. Country-drop etc.
 (defn reactive-input [cursor val key]
   [:input {:type "text" :value val :class "form-control"
            :onChange
            #(om/transact! cursor key (fn [_] (.. % -target -value)))}])

(defn progress-bar [percent]
  [:div {:class "progress"}
   [:div {:class "progress-bar"
          :style #js {:width (str percent ";")}}]])



;; FORM GROUPS

(def country-list [["AT" "Austria"]
                   ["BE" "Belgium"]
                   ["BG" "Bulgaria"]
                   ["CH" "Switzerland"]
                   ["CY" "Cyprus"]
                   ["CZ" "Czech Republic"]
                   ["DE" "Germany"]
                   ["DK" "Denmark"]
                   ["EE" "Estonia"]
                   ["ES" "Spain"]
                   ["FI" "Finland"]
                   ["FR" "France"]
                   ["GB" "United Kingdom"]
                   ["GR" "Greece"]
                   ["HR" "Croatia"]
                   ["HU" "Hungary"]
                   ["IE" "Ireland"]
                   ["IS" "Iceland"]
                   ["IT" "Italy"]
                   ["LI" "Liechtenstein"]
                   ["LT" "Lithuania"]
                   ["LU" "Luxembourg"]
                   ["LV" "Latvia"]
                   ["MC" "Monaco"]
                   ["MT" "Malta"]
                   ["NL" "Netherlands"]
                   ["NO" "Norway"]
                   ["PL" "Poland"]
                   ["PT" "Portugal"]
                   ["RO" "Romania"]
                   ["SE" "Sweden"]
                   ["SI" "Slovenia"]
                   ["SK" "Slovakia"]
                   ["SM" "San Marino"]])

(defn form-group [name cursor ks]
  [:div.form-group
   [:label {:class "col-lg-2 control-label"} name]
   [:div.col-lg-10 (reactive-input cursor (get-in cursor ks) ks)]])

(defn form-group-country [cursor]
  [:div.form-group
   [:label {:for "address[country_code]"
            :class "col-lg-2 control-label"} "Country"]
   [:div.col-lg-10

    [:select {:id "address[country_code]"
              :name "address[country_code]"
              :class "form-control"
              :style #js {:width "100%;"}
              :value (-> cursor :address :country_code)
              :onChange
              #(om/transact! cursor [:address :country_code]
                             (fn [_] (.. % -target -value)))}
     [:option {:value ""} "(Select a country)"]
     [:option {:value "DE"} "Germany"]
     (for [n country-list]
       [:option {:value (first n)} (second n)])]]])

(defn form-group-callback-method [cursor]
  [:div.form-group
   [:label {:for "callback_method"
            :class "col-lg-2 control-label"} "Callback Method"]
   [:div.col-lg-10
    [:select {:id "callback_method"
              :name "callback_method"
              :class "form-control"
              :style #js {:width "100%;"}
              :value (-> cursor :callback :method)
              :onChange
              #(om/transact! cursor [:callback :method]
                             (fn [_] (.. % -target -value)))}
     [:option {:value "POST"} "POST"]
     [:option {:value "GET"} "GET"]]]])

(defn edit-user-form-groups [user chan]
  [:form.form-horizontal
   [:fieldset]
   [:div.row
    [:div.col-lg-6
     ;;[:legend "Company details"]
     (form-group "Company name" user [:display_name])
     (form-group "Website" user [:uri])
     (form-group "Contact email" user [:contact_email])
     (form-group "Contact phone" user [:contact_phone])
     (form-group "VAT" user [:vat])]
    [:div.col-lg-6
     ;;[:legend "Address"]
     (form-group "Address (line 1)" user [:address :line1])
     (form-group "Address (line 2)" user [:address :line2])
     (form-group "City" user [:address :city])
     (form-group "State" user [:address :state])
     (form-group "Postal code" user [:address :postal_code])
     (form-group-country user)]]
   [:button {:type "button" :class "btn btn-primary"
             :on-click (fn [e] (put! chan {:tag :put-merchant :value @user}))}
    "Update merchant"]])

(defn edit-user-technical-form-groups [user chan]
  [:form.form-horizontal
   [:fieldset]
   [:div.row
    [:div.col-lg-6
     [:legend "Callback details"]
     (form-group "Reference name" user [:name])
     (form-group "Callback URI for webhooks" user [:callback :uri])
     (form-group-callback-method user)
     [:br]]]
   [:button {:type "button" :class "btn btn-primary"
             :on-click (fn [e] (put! chan {:tag :put-merchant :value @user}))}
    "Update merchant"]])

(defn edit-customer-form-groups [token mode customer chan]
  [:form.form-horizontal
   [:fieldset]
   [:div.row
    [:div.col-lg-6
     [:legend "Post a customer"]
     (form-group "Email" customer [:email])
     (form-group "Name" customer [:name])
     (form-group "Address" customer [:address :line1])
     (form-group "City" customer [:address :city])
     (form-group "State" customer [:address :state])
     (form-group "Postal code" customer [:address :postal_code])
     (form-group-country customer)
     [:br]]]
   [:button {:type "button" :class "btn btn-primary"
             :on-click (fn [e] (put! chan
                                     {:tag :post-customer
                                      :value (assoc @customer
                                                    :mode mode
                                                    :token token)}))}
    "Add customer"]])


;; {:token (-> @app :user :key)
;;         :cid (-> @app :tour :cid)
;;         :number "0000001"
;;         :items [{:amount "999" :description "Box of delicious candy!"}]}}))
(defn edit-debit-form-groups [token mode cemail cid debit chan]
   (if cemail
     [:form.form-horizontal
      [:fieldset]
      [:div.row
       [:div.col-lg-6
        [:legend (str "Post a debit to " cemail)]
        ;; for one item
       (form-group "Number" debit [:number])
        (form-group "Description" debit [:temp-item :description])
        (form-group "Amount (eurocent)" debit [:temp-item :amount])
        (form-group "VAT percent" debit [:temp-item :vat_percent])
        (form-group "Quantity" debit [:temp-item :quantity])
        [:br]]]
      [:button {:type "button" :class "btn btn-primary"
                :on-click (fn [e] (put! chan
                                        {:tag :post-debit
                                         :value (assoc @debit
                                                       :items [(:temp-item @debit)]
                                                       :mode mode
                                                       :token token
                                                       :cid cid)}))}
       "Post debit"]]
     [:h3 "Please add a customer first."]))



;; COMPONENTS

(defn input-box [cursor owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [event-chan]}]
      (if-let [key (get-query-key (js/window.location.toString))]
        (put! event-chan {:tag :auth :value {:key key}}))
      (html [:div {:class "form-inline" :role "form"}
             (input-box-input owner event-chan)
             (input-box-button owner event-chan)]))))

(defn progress-view [app _]
  (om/component
   (html
    [:div {:id "error-view"
           :style (hidden (-> app :hidden :progress))
           :class "alert alert-dismissable alert-info"}
     [:strong (-> app :progress :message)]])))

(defn error-view [app _]
  (om/component
   (html
    [:div {:id "error-view"
           :style (hidden (-> app :hidden :error))
           :class "alert alert-danger"}
     [:strong "Oh snap! "] (-> app :error :message)])))

(defn header-view [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [event-chan]}]
      (html
       [:div {:class "navbar navbar-default navbar-fixed-top"}
        [:div {:class "container"}
         [:div {:class "navbar-header"}
          (gen-link "App" event-chan {:tag :go-home} "navbar-brand")
          [:button {:class "navbar-toggle" :data-target "#navbar-main"
                    :data-toggle "collapse" :type "button"
                    :on-click (fn [e] (put! event-chan
                                            {:tag :go-home}))} ;; or show js menu...
	           [:span {:class "icon-bar"}]
           [:span {:class "icon-bar"}]
           [:span {:class "icon-bar"}]
           [:span {:class "icon-bar"}]]]
         
         [:div {:class "navbar-collapse collapse" :id "navbar-main"}

          (when (-> app :user :key)
            [:ul {:class "nav navbar-nav"}]
            [:ul {:class "nav navbar-nav navbar-left"}
             [:li (gen-link "Tour" event-chan {:tag :tour/go-first} "")]
             [:li (gen-link "Account" event-chan
                            {:tag :get-merchant
                             :value {:mode (-> app :user :mode)
                                     :user (-> app :user :id)}} "")]
             [:li (gen-link "Transactions" event-chan
                            {:tag :get-statement
                             :value {:mode (-> app :user :mode)
                                     :key (-> app :user :key)
                                     :id (-> app :user :id)}} "")]
             [:li (gen-link "Customers" event-chan
                            {:tag :get-customers
                             :value {:mode (-> app :user :mode)
                                     :token (-> app :user :key)
                                     :id (-> app :user :id)}} "")]

             [:li (gen-link "Debits" event-chan {:tag :go-debit} "")]])
          
          [:ul {:class "nav navbar-nav navbar-right"}
           ;;[:li [:a {:href "https://invoice.vaulted.com"}
                ;;         "Invoice Maker"]]

           [:li [:a {:href "https://www.vaulted.com/seller/api"}
                 "Check out our API"]]
           [:li [:a {:href "https://twitter.com/vaultedmotd"} "Twitter"]]
           [:li [:a {:href "http://vaulted.com"}
                 "Built by Vaulted. v.0.4.0"]]]]]]))))

(defn auth-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [event-chan (om/get-state owner [:event-chan])]
        (html
         [:div
          {:id "auth-view" :style (hidden (-> app :hidden :auth))}
          [:h1 "Enter API-key"]
          (om/build input-box (:input (:user app)) ;;huh?
	   {:init-state {:event-chan event-chan}})])))))

(defn welcome-view [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [event-chan]}]
      (html
       [:div {:style (hidden (-> app :hidden :welcome))}
        [:h1 (str (or (-> app :user :display_name) "Welcome!")
                  (if (= :live (-> app :user :mode))
                    " (live mode)"
                    " (test mode)"))]
        ;; view info
          [:div {:class "btn-group"}
           [:p
            (gen-button "Tour" "btn-default"
                        event-chan {:tag :tour/go-first})
            (gen-button "Account settings" "btn-default"
                        event-chan {:tag :get-merchant :value
                                    {:mode (-> app :user :mode) :user (-> app :user :id)}})
            (gen-button "Transactions" "btn-default"
                        event-chan {:tag :get-statement :value {:mode (-> app :user :mode)
                                                                :key (-> app :user :key) :id (-> app :user :id)}})
            (gen-button "Customers" "btn-default"
                        event-chan {:tag :get-customers})
            (gen-button "Debits" "btn-default"
                        event-chan {:tag :go-debit})]]]))))

(defn tour-view-one [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [event-chan]}]
      (html
       [:div {:style (hidden (-> app :hidden :tour/first))}
        (progress-bar "33%")
        [:h1 (str "Welcome to the Vaulted Tour")]
        [:h3 "Try sending an invoice"]
        [:div {:class "form-inline" :role "form"}
         [:div {:class "form-group"}
          (reactive-input app (-> app :tour :email) [:tour :email])]
         ;;[:input {:type "text" :value (str (-> app :user :contact_email))}]
         (gen-button "Send invoice" "btn-primary"
                     event-chan {:tag :send-invoice
                                 :value {:token (-> app :user :key)
                                         :email (-> app :tour :email)
                                         :name "Sterling Archer"}})]
        [:br]
        [:p "or paste the following into your terminal to create a customer"]
        [:p
         [:pre {:rows "10"}
          (str "curl https://test.vaulted.com/v0/customers -X POST \\
-u ")
          (str (-> app :user :key))
          ;;[:span {:class "user-input"} (-> app :user :key)]

          (str ": -H \"Content-Type: application/json\" \\
-d '{ \"name\" : \"Sterling Archer\", \"email\" : " \")
          [:span {:class "user-input"} (-> app :tour :email)]
          (str \" " }'")]]

        ;; div cols? meh, damn margins. GSS!
     [:div {:class "form-inline"} ;; not really but reduces width
     [:p "Enter the user id you get back: "
      (reactive-input app (-> app :tour :cid) [:tour :cid])]]
        [:p "Then paste the following to create a debit"
         [:pre 
          (str "curl https://test.vaulted.com/v0/customers/")
          [:span {:class "user-input"} (-> app :tour :cid)]
          (str "/debits -X POST \\
-u \"")
          (str (-> app :user :key))
          ;;[:span {:class "user-input"} (-> app :user :key)]
	(str "\":  -H \"Content-Type: application/json\" \\
-d '{ \"number\" : \"000001\", \"items\" : [{ \"amount\" : 999 , \"description\" : \"Box of delicious candy\"}]}'")]]
        [:br]
        (gen-button "Next" "btn-primary"
                    event-chan {:tag :tour/get-statement
                                :value {:key (-> app :user :key) :id (-> app :user :id)}})]))))

(defn tour-cta-view [app owner]
  (reify
    om/IRender
    (render [_]
      (let [event-chan (om/get-state owner [:event-chan])]
        (html
         [:div 
          {:id "tour-cta-view"
           :style (hidden (-> app :hidden :tour/cta))}
          (progress-bar "100%")
          [:h1 "End of tour."]
          ;; [:button {:type "button" :class (str "btn " type) :href "foobar"
 ;;            :on-click (fn [e] (put! chan msg))}
  
          [:a {:href "mailto:hi@vaulted.com" :type "button"
               :class "btn btn-primary btn-lg"}
           "Get a live key"]
          [:h2 "Psst..."]
          [:p "you can also "
           (gen-link "create customers" event-chan {:tag :get-customers} "") ", "
           (gen-link "post debits" event-chan {:tag :go-debit} "") " (with created customers), "
           [:p "and set up "
            (gen-link "your account" event-chan {:tag :get-merchant :value
                                                 {:mode (-> app :user :mode)
                                                  :user (-> app :user :id)}} "")
            " (including callbacks!)"]]])))))

(defn user-view [app owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [event-chan]}]
      (html
       [:div {:style (hidden (-> app :hidden :user))}
        [:div.row
         [:div {:class "col-lg-12"}
          [:h2 "Account settings"]
          [:p (gen-button "Technical integration" "btn-default"
                          event-chan {:tag :go-technical})]]]
        [:div.well
         (edit-user-form-groups (:user app) event-chan)]]))))

(defn statement-view
  "A view for one individual statement."
  [statement owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [event-chan]}]
      (html
       [:tr
        (for [k [:created_at :reference :amount :instrument
                 :customer :id :status]]
		 [:td (get (:history statement) k)]
           ;; XXX: Don't have access to app here. Tour still uses this code.
          #_(if (= k :customer)
	   [:td (gen-link "Customers" event-chan
		   {:tag :get-customers
		   :value {:mode (-> app :user :mode)
		           :token (-> app :user :key)
			   :id (-> app :user :id)}} "")]
            [:td (get (:history statement) k)]))]))))

(defn customer-view
  "A view for one individual customer."
  [cust owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [event-chan]}]
      (html
       [:tr
        ;; XXX: Non standard because of non-restful API call.
        [:td (-> cust :user :id)]
        [:td (-> cust :user :profile :profile :addr first :address :name)]
        [:td (-> cust :user :profile :profile :contact_email)]
        [:td (-> cust :user :profile :profile :addr first :address :country_code)]
        #_(for [k [:display_name :contact_email :country]]
          [:td (get (:profile (:profile (:user cust))) k)])]))))

(defn customers-view [app owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [event-chan]}]
      (html
       [:div {:style (hidden (-> app :hidden :customer))}

        [:div {:id "customers"}
         [:h2 "Customer list"]
         [:table {:className "table table-striped table-hover"}
          [:thead
           [:tr (for [n ["ID" "Customer name" "Email" "Country"]] ;; Country?
                  [:th
                   [:a {:href "#"
                        :class "sort-column"
                        :on-click (fn [e]
                                    (put! event-chan
                                          {:tag :sort/transactions
                                           :value {:by n}}))}
                    n
                    #_[:span {:class "caret caret-reversed"}]
                    #_[:span {:class "caret"}]]])]]
          [:tbody
           (om/build-all customer-view
                         (get-in app [:customers]))]]

		[:div.row
		 [:div {:class "col-lg-12"}
		  [:h2 "Add a customer"]
		  [:p [:button {:type "button" :class "btn btn-primary"
				:on-click (fn [e] (put! event-chan {:tag :clear-customer}))}
		       "Reset customer"]]]]
		[:div.well
		 (edit-customer-form-groups (-> app :user :key)
					    (-> app :user :mode)
					    (:customer app)
					    event-chan)]]]))))

(defn debit-view [app owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [event-chan]}]
      (html
       [:div {:style (hidden (-> app :hidden :debit))}
        [:div.row
         [:div {:class "col-lg-12"}
          [:h2 "Make a debit"]]]
        [:div.well
         ;; TODO: make map or group somehow
        (edit-debit-form-groups (-> app :user :key)
                                (-> app :user :mode)
                                (-> app :customer :email)
                                (-> app :customer :id)
                                (:debit app)
                                event-chan)]]))))


(defn user-technical-view [app owner]
  (reify
    om/IRenderState
    (render-state [this {:keys [event-chan]}]
      (html
       [:div {:style (hidden (-> app :hidden :account/technical))}
        [:div.row
         [:div {:class "col-lg-12"}
          [:h2 "Technical integration"]]]
        [:div.well
         (edit-user-technical-form-groups (:user app) event-chan)]]))))

(defn statements-view
  "A table for all the statement records."
  [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [event-chan]}]
      (html
       [:div {:style (hidden (-> app :hidden :statements))}
        [:div {:id "statements"}
         [:h2 "Your transactions"]
         (gen-button "Download as CSV" "btn-primary"
                     event-chan
                     {:tag :get-statement-csv
                      :value {:key (-> app :user :key) :id (-> app :user :id)}})
         [:table {:className "table table-striped table-hover"}
          [:thead
           [:tr (for [n ["Created at" "Reference" "Amount"
                         "Instrument" "Customer" "Payment ID" "Status"]]
                  [:th
                   [:a {:href "#"
                        :class "sort-column"
                        :on-click (fn [e]
                                    (put! event-chan
                                          {:tag :sort/transactions
                                           :value {:by n}}))}
                    n
                    #_[:span {:class "caret caret-reversed"}]
                    #_[:span {:class "caret"}]]])]]
          [:tbody
		(for [statement (get-in app [:statements :history])]
                  [:tr
		  (for [k [:created_at :reference :amount :instrument
			 :customer :id :status]]
		    (if (= k :customer)
		      [:td (gen-link (get (:history statement) k) event-chan
			     {:tag :get-customers
			     :value {:mode (-> app :user :mode)
				     :token (-> app :user :key)
				     :id (-> app :user :id)}} "")]
		      [:td (get (:history statement) k)]))])]]]]))))

;; NOTE: still uses statement-view without link
(defn tour-statements-view
  "A table for all the statement records."
  [app owner]
  (reify
    om/IRenderState
    (render-state [_ {:keys [event-chan]}]
      (html
       [:div {:style (hidden (-> app :hidden :tour/statements))}
        [:div {:id "statements"}
         (progress-bar "66%")
         [:h2 "Your transactions"]
         [:p (str "The status of this example debit will start off as pending,
and, since you are using the test API, we are automatically settling the payment
within a minute of its creation. The status will then be succeeded. (Remember that the invoice is in the inbox of "
                  (-> app :tour :email) " too).")]
         [:p (gen-button "Update" "btn-primary"
                         event-chan {:tag :tour/get-statement
                                     :value {:key (-> app :user :key) :id (-> app :user :id)}})]
         [:table {:className "table table-striped table-hover"}
          [:thead
           [:tr (for [n ["Created at" "Reference" "Amount"
                         "Instrument" "Customer" "Payment ID" "Status"]]
                  [:th n])]]
          [:tbody
           ;; how to sort?
           (om/build-all statement-view
                         (get-in app [:statements :history]))]]
         [:p (gen-button "Next" "btn-primary" event-chan {:tag :tour/go-cta-view})]]]))))

(defn app-view [app owner]
  (reify
    om/IInitState
    (init-state [_]
      (state/set-base-url!)
      {:chans {:event-chan (chan (sliding-buffer 1))}})
    om/IWillMount
    (will-mount [_]
      (let [event-chan (om/get-state owner [:chans :event-chan])]
        (controllers/event-loop app owner event-chan)))
    om/IRenderState
    (render-state [_ {:keys [chans]}]
      (html
       [:div {:class "container"}
        (om/build header-view app {:init-state chans})
        [:div {:class "container extra-space"}
         ;;[:p (str "APP STATE: " @state/app-state)] ;; debug
         (om/build progress-view app)
         (om/build error-view app)
         (om/build auth-view app {:init-state chans})
         (om/build welcome-view app {:init-state chans})
         (om/build tour-view-one app {:init-state chans})
         (om/build tour-statements-view app {:init-state chans})
         (om/build tour-cta-view app {:init-state chans})
         (om/build user-view app {:init-state chans})
         (om/build user-technical-view app {:init-state chans})
         (om/build customers-view app {:init-state chans})
         (om/build debit-view app {:init-state chans})
         (om/build statements-view app {:init-state chans})]]))))

