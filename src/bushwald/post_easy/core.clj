(ns bushwald.post-easy.core
  (:require [bushwald.post-easy.schemas :as ep-schema]
            [clj-http.client :as http]
            [clojure.data.json :as json]))

(def easypost-api-url "https://api.easypost.com/v2/")

(defn- get-env-token [] (System/getenv "EASYPOST_TOKEN"))

(defn- post-easypost [uri token body]
  (try (http/post (str easypost-api-url uri)
                  {:basic-auth [token ""]
                   :body body
                   :content-type :json
                   :accept :json
                   :socket-timeout (or (System/getenv "EP_SOCKET_TIMEOUT") 5000)
                   :connection-timeout
                     (or (System/getenv "EP_CONNECTION_TIMEOUT") 20000)})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [status reason-phrase body]} (ex-data e)]
           (throw (ex-info (str "EasyPost error: " reason-phrase)
                           {:status status
                            :body (json/read-str body :key-fn keyword)}))))))

(defn- get-easypost [uri token]
  (try (http/get (str easypost-api-url uri)
                 {:basic-auth [token ""]
                  :accept :json
                  :socket-timeout (or (System/getenv "EP_SOCKET_TIMEOUT") 5000)
                  :connection-timeout
                  (or (System/getenv "EP_CONNECTION_TIMEOUT") 20000)})
       (catch clojure.lang.ExceptionInfo e
         (let [{:keys [status reason-phrase body]} (ex-data e)]
           (throw (ex-info (str "EasyPost error: " reason-phrase)
                           {:status status
                            :body (json/read-str body :key-fn keyword)}))))))

(defn- easypost-request
  ([ep-map uri token] (easypost-request uri :post token))
  ([ep-map uri http-verb token]
   (let [body (json/write-str ep-map)
         request-func (cond (= http-verb :post)
                            post-easypost
                            (= http-verb :get)
                            (get-easypost))
         response (post-easypost uri token body)]
     (json/read-str (:body response) :key-fn keyword))))


;; ADDRESS
(defn create-address
  ([address] (create-address address get-env-token))
  ([address token]
   (easypost-request (ep-schema/validate-address address) "addresses" token)))

(defn verify-address
  ([address-id] (verify-address address-id get-env-token))
  ([address-id token]
   (easypost-request {} (str "addresses/" address-id "/verify") :get token)))

(defn retrieve-address
  ([address-id] (retrieve-address address-id get-env-token))
  ([address-id token]
   (easypost-request {} (str "addresses/" address-id) :get token)))

(defn retrieve-all-addresses
  ([num-pages] (retrieve-all-addresses num-pages get-env-token))
  ([num-pages token]
   (easypost-request {} (str "addresses?page_size=" num-pages) :get token)))

;;PARCEL
(defn create-parcel
  ([parcel] (create-parcel parcel get-env-token))
  ([parcel token]
   (easypost-request (ep-schema/validate-parcel parcel) "parcels" token)))

(defn retrieve-parcel
  ([parcel-id] (retrieve-parcel parcel-id get-env-token))
  ([parcel-id token]
   (easypost-request {} (str "parcels/" parcel-id) :get token)))

;;RATE
(defn retrieve-rate
  ([rate-id] (retrieve-rate rate-id get-env-token))
  ([rate-id token]
   (easypost-request {} (str "rates/" rate-id) :get token)))

;;SHIPMENT
(defn create-shipment
  ([shipment] (create-shipment shipment get-env-token))
  ([shipment token]
   (easypost-request (ep-schema/validate-shipment shipment) "shipments" token)))

(defmulti buy-shipment
  (fn [buy-map & args] (first (keys buy-map))))

(defmethod buy-shipment :rate
  ([buy-map] (buy-shipment buy-map get-env-token))
  ([buy-map token]
   (easypost-request
    (ep-schema/validate-shipment-buy buy-map)
    (str "shipments/" (:shipment_id (:rate buy-map)) "/buy")
    token)))

(defmethod buy-shipment :shipment ;; one-call buy
  ([buy-map] (buy-shipment buy-map get-env-token))
  ([buy-map token]
   (easypost-request
    (ep-schema/validate-shipment buy-map) "shipments" token)))

(defn retrieve-shipment
  ([shipment-id] (retrieve-shipment shipment-id get-env-token))
  ([shipment-id token]
   (easypost-request {} (str "shipments/" shipment-id) :get token)))

(defn retrieve-all-shipments
  ([num-pages] (retrieve-all-shipments num-pages get-env-token))
  ([num-pages token]
   (easypost-request {} (str "shipments?page_size=" num-pages) :get token)))

(defn get-lowest-rate [rates]
  (first (sort-by #(new BigDecimal (:rate %)) rates)))

(defn refund-shipment
  ([shipment-id] (refund-shipment shipment-id get-env-token))
  ([shipment-id token]
   (easypost-request {} (str "shipments/" shipment-id "/refund") token)))

(defn insure-shipment
  ([shipment-id] (insure-shipment shipment-id get-env-token))
  ([shipment-id token]
   (easypost-request {} (str "shipments/" shipment-id "/insure") token)))

(defn re-rate-shipment
  ([shipment-id] (re-rate-shipment shipment-id get-env-token))
  ([shipment-id token]
   (easypost-request {} (str "shipments/" shipment-id "/rerate") token)))

(defn retrieve-smartrate
  ([shipment-id] (re-rate-shipment shipment-id get-env-token))
  ([shipment-id token]
   (easypost-request {} (str "shipments/" shipment-id "/smartrate") :get token)))

;;INSURANCE
(defn create-insurance
  ([insurance] (create-insurance insurance get-env-token))
  ([insurance token]
   (easypost-request (ep-schema/validate-insurance insurance) "insurances" token)))

(defn retrieve-insurance
  ([insurance-id] (retrieve-insurance insurance-id get-env-token))
  ([insurance-id token]
   (easypost-request {} (str "insurances/" insurance-id) :get token)))

(defn retrieve-all-insurance
  ([num-pages] (retrieve-all-insurance num-pages get-env-token))
  ([num-pages token]
   (easypost-request {} (str "insurances?page_size=" num-pages) :get token)))

;;TRACKERS
(defn create-tracker
  ([tracker] (create-tracker tracker get-env-token))
  ([tracker token]
   (easypost-request (ep-schema/validate-tracker tracker) "trackers" token)))

(defn retrieve-tracker
  ([tracker-id] (retrieve-tracker tracker-id get-env-token))
  ([tracker-id token]
   (easypost-request {} (str "trackers/" tracker-id) :get token)))

(defn retrieve-all-trackers
  ([num-pages] (retrieve-all-trackers num-pages get-env-token))
  ([num-pages token]
   (easypost-request {} (str "trackers?page_size=" num-pages) :get token)))
