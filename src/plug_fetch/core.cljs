(ns plug-fetch.core
  (:require [ajax.json :as json]
            [ajax.transit :as transit]
            [re-frame.core :as rf]))


;|-------------------------------------------------
;| HELPERS

(defn process-fx
  "We allow passing functions inside the list of fx to allow result dependant dispatching
  Example: :ok-fx [[dispatch [:foo]] #(if ,,,) ]
  The ones that are functions will be passed the data to turn it into the desired fx.
  Useful to e.g. dispatch a specific event if the number of results is 0"
  [fx-list data]
  (some->> fx-list
           (map (fn [fx]
                  (cond
                    (fn? fx) (fx data)
                    :else-keep fx)))))


(defn redirect-client
  "Redirect client to provided url, or to the login screen if no url given."
  ([] (redirect-client "/login"))
  ([url] (set! (.-location js/document) url)))


;|-------------------------------------------------
;| FX HANDLERS

(defn- ok-handler [{:keys [db]} [{:keys [ok-fx result-path result-merge-in result-fn]
                                  :as   opts}
                                 result]]
  {:pre  [(or (nil? result-path) (sequential? result-path))
          (or (nil? result-merge-in) (sequential? result-merge-in))]
   :post [(or (nil? %) (map? %))]}
  (let [result-to-store (if result-fn (result-fn result) result)]
    (merge
      (when (or result-path result-merge-in)
        {:db (cond-> db
                     result-path (assoc-in result-path result-to-store) ;; Typically either this ..
                     result-merge-in (update-in result-merge-in merge result-to-store))}) ;; .. or this, but you can have both
      (when ok-fx {:fx (process-fx ok-fx result)}))))


;|-------------------------------------------------
;| HANDLE OUTCOME OF FETCH EVENT

(rf/reg-event-fx
  ::ok
  [rf/trim-v]
  ok-handler)


(rf/reg-event-fx
  ::nok
  [rf/trim-v]
  (fn [_ [{:keys [id nok-fx] :as opts} {:keys [status last-error] :as err}]]
    (cond
      ;; Ensure failed API requests causes appropriate redirect to login (and then back to the current hash route)
      (= 401 status)
      (redirect-client (str "/login" (some-> js/document .-location .-hash)))

      :else-treat-as-generic-error
      {:fx (concat (process-fx nok-fx err)
                   [[:dispatch [:reg/error {:id      id
                                            :message last-error
                                            :raw     err}]]])})))


;|-------------------------------------------------
;| GENERIC FETCHING

(defn make-fx-map-for-backend-event
  "Make fx map for re-frame's reg-event-fx event handler."
  [{:keys [method id uri fx params timeout] :as opts}]
  {:fx (concat fx
               [[:http-xhrio (merge {:method          (or method :get)
                                     :uri             uri
                                     :format          (transit/transit-request-format) ; Transit ensures namespaced keys survive
                                     :response-format (json/json-response-format {:keywords? true}) ;; IMPORTANT!: You must provide this.
                                     :on-success      [::ok opts]
                                     :on-failure      [::nok opts]}
                                    (when params {:params params})
                                    (when timeout {:timeout timeout}))]])})
