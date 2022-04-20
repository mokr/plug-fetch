(ns plug-fetch.core
  (:require [ajax.edn :as edn]
    ;[ajax.json :as json]
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

(defn- ok-handler [{:keys [db]} [{:keys [ok-fx result-path result-event result-merge-in result-fn]
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
  (fn [_ [{:keys [nok-fx] :as opts} {:keys [status last-error uri last-method] :as err}]]
    (cond
      ;; Ensure failed API requests causes appropriate redirect to login (and then back to the current hash route)
      (= 401 status)
      (redirect-client (str "/login" (some-> js/document .-location .-hash)))

      :else-treat-as-generic-error
      {:fx (concat (process-fx nok-fx err)
                   [[:dispatch [:reg/error {:source  "plug-fetch"
                                            :action  (str last-method " " uri)
                                            :message last-error
                                            :raw     err}]]])})))


;|-------------------------------------------------
;| GENERIC FETCHING

(defn make-fx-map-for-backend-event
  "Make fx map for re-frame's reg-event-fx event handler.

  Options affecting request:
  --------------------------
  :uri               [req] URI endpoint to send request to
  :method            [opt] Method for the request (default :get)
  :fx                [opt] Re-frame fx that will be dispatched before :http-xhrio event
  :params            [opt] Params to send in :params
  :timeout           [opt] Timeout value for request

  Options affecting result / after request:
  -----------------------------------------
  :result-fn          [opt] If provided, result from server will be treated by it before storing
  :result-path        [opt] Path to store result (possibly treated by :result-fn)
  :result-event       [opt] Send result as last entry in this event vector (possibly treated by :result-fn). Useful when more involved processing is needed before incorporation result in DB.
  :result-merge-in    [opt] app-db path that will be updated by merging in the result (possibly treated by :result-fn)
  :ok-fx              [opt] Re-frame fxs to send if request OK. Vector of event vectors (same as re-frame :fx)
  :nok-fx             [opt] Re-frame fxs to send if request not OK. Vector of event vectors (same as re-frame :fx)"
  [{:keys [method uri fx params timeout] :as opts}]
  {:fx (concat fx
               [[:http-xhrio (merge {:method          (or method :get)
                                     :uri             uri
                                     :format          (transit/transit-request-format) ; Transit ensures namespaced keys survive
                                     :response-format (transit/transit-response-format)
                                     :on-success      [::ok opts]
                                     :on-failure      [::nok opts]}
                                    (when params {:params params})
                                    (when timeout {:timeout timeout}))]])})
