(ns app.state)

(def base-url (atom ""))

(defn set-base-url! []
  (swap! base-url (fn [_] (str (.-origin (.-location js/window))))))

(defonce app-state
         (atom {:error {:message ""}
                :progress {:message ""}
                :tour {:input ""}
                :view :welcome
                :hidden {:auth false
                         :error true
                         :progress true
                         :statements true
                         :welcome true
                         :customer true
                         :debit true
                         :tour/first true
                         :tour/statements true
                         :tour/cta true
                         :account/technical true
                         :user true}
                :user {:input {}}
                :customer {}
                :debit {}
                :statements {}}))
