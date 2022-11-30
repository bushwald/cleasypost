(ns bushwald.cleasypost.core
  (:require [bushwald.cleasypost.schemas :as ep-schema]
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

(defn- easypost-request [ep-map uri token]
  (let [body (json/write-str ep-map)
        response (post-easypost uri token body)]
    (json/read-str (:body response) :key-fn keyword)))

(defn create-address
  ([address] (create-address address get-env-token))
  ([address token]
   (easypost-request (ep-schema/validate-address address) "addresses" token)))

(defn create-parcel
  ([parcel] (create-parcel parcel get-env-token))
  ([parcel token]
   (easypost-request (ep-schema/validate-parcel parcel) "parcels" token)))

(defn create-shipment
  ([shipment] (create-shipment shipment get-env-token))
  ([shipment token]
   (easypost-request (ep-schema/validate-shipment shipment) "shipments" token)))

(defn create-insurance
  ([insurance] (create-insurance insurance get-env-token))
  ([insurance token]
   (easypost-request (ep-schema/validate-insurance insurance) "insurances" token)))

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

(defn retrieve-rate
  ([rate-id] (retrieve-rate rate-id get-env-token))
  ([rate-id token]
   (easypost-request {} (str "rates/" rate-id) token)))
