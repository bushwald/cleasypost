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


(comment
(def origin-adr
  {:address
   {:name "Steve Brule"
    :street1 "1849 Geary Blvd"
    :street2 "Apt C"
    :city "San Francisco"
    :state "NY"
    :zip "94115"
    :country "US"
    :phone "4155555555"}})

(def destination-adr
  {:address
   {:name "bushwald"
    :street1 "16 Arcade"
    :city "Nashville"
    :state "TN"
    :zip "37219"
    :country "US"
    :phone "7185555555"}
   :verify true})

(def prcl
  {:parcel
   {:length 4
    :width 4
    :height 6.2
    :weight 32}})

(def shpmnt {:to_address (:address origin-adr)
             :from_address (:address destination-adr)
             :parcel (:parcel prcl)})

(def token "EZ299nnTK2ffkdsefjlsiejfake_tokenR1piAarlSa6vA234RsA")

(def ep-shpmnt (create-shipment {:shipment shpmnt} token))

(def one-call-buy-shpmnt
  {:shipment (merge shpmnt
                    {:service "Priority"
                     :carrier_accounts ["ca_e61a814d26testa90b663a5zz053d18"]})})

(:postage_label (buy-shipment one-call-buy-shpmnt token))

(:postage_label (buy-shipment {:rate (get-lowest-rate (:rates ep-shpmnt))} token))
)
