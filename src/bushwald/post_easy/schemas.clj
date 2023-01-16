(ns bushwald.post-easy.schemas
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.error :as me]
            [malli.transform :as mt]
            [sci.core :as sci]))

;; The schemas here are meant to represent actual user inputs.
;; Properties that are filled in in an EasyPost response are not included.

(def schema-transformer
  (mt/transformer
   (mt/default-value-transformer {::mt/add-optional-keys true})))

(defn- get-validated-map
  "given a schema and a map, validate that the map conforms to the schema
  and return the result with default values included, else throw error"
  [schema ep-map]
  (if (m/validate schema ep-map)
    (m/decode schema ep-map schema-transformer)
    (let [error-msg (me/humanize (m/explain schema ep-map))]
      (throw (java.io.InvalidObjectException. (str error-msg))))))

(def non-empty-string?
  (m/from-ast {:type :string
               :properties {:min 1}}))

(def iso-date-string?
  (m/-simple-schema
   {:type :user/address-obj
    :pred #(instance? java.time.ZonedDateTime
                      (try (java.time.ZonedDateTime/parse %)
                           (catch Exception e false)))
    :type-properties {:error/message "must be ISO 8601 formatted date string"
                      :json-schema/type "string"}}))

(defn str-vec-schema
  "given a name and a vector of strings, return a schema that
  checks whether a word matches one of the items in the collection"
  [name str-vec]
  (m/-simple-schema
   {:type (keyword (str "user/" name))
    :pred #(contains? str-vec %)
    :type-properties
    {:error/message
     (str "must be one of '" (str/join "','" str-vec) "'")
     :json-schema/type "string"}}))

(def mode-str?
  (m/-simple-schema
   {:type :user/address-obj
    :pred #(or (= "test" %) (= "production" %))
    :type-properties {:error/message "must be either 'test' or 'production'"
                      :json-schema/type "string"}}))

;; Address
;; http://www.iso.org/iso/country_codes
(defn iso-country-code [code]
  (or
   (and (number? (read-string code))
        (< (read-string code) 1000) (> (read-string code) 99))
   (and (string? code) (< (count code) 4) (> (count code) 1))))

(def iso-country-code?
  (m/-simple-schema
   {:type :user/iso-country-code
    :pred iso-country-code
    :type-properties
    {:error/message
     "country code must conform to ISO 3166 http://www.iso.org/iso/country_codes"
     :json-schema/type "string"}}))

(def address
  [:map
   [:mode
    {:optional true :default "test"}
    [:and non-empty-string? mode-str?]]
   [:street1 non-empty-string?]
   [:street2 {:optional true} non-empty-string?]
   [:city {:optional true} non-empty-string?]
   [:state {:optional true} non-empty-string?]
   [:zip {:optional true} non-empty-string?]
   [:country [:and non-empty-string? iso-country-code?]]
   [:residential {:optional true} boolean?]
   [:carrier_facility {:optional true} non-empty-string?]
   [:name non-empty-string?]
   [:company {:optional true} non-empty-string?]
   [:phone {:optional true} non-empty-string?]
   [:email {:optional true} non-empty-string?]
   [:federal_tax_id {:optional true} non-empty-string?]
   [:state_tax_id {:optional true} non-empty-string?]])

(def address-request
  [:map
   [:address address]
   [:verify {:optional true} boolean?]
   [:verify_strict {:optional true} boolean?]])

(defn validate-address [address-map]
  (get-validated-map address-request address-map))

;; Parcel
(defn dim-checker [schema-keys]
  (let [length (:length schema-keys)
        width (:width schema-keys)
        height (:height schema-keys)]
    (not (or (and (nil? length)
                  (or (and (not (nil? width)) (>= width 0))
                      (and (not (nil? height)) (>= height 0))))
             (and (nil? width)
                  (or (and (not (nil? length)) (>= length 0))
                      (and (not (nil? height)) (>= height 0))))
             (and (nil? height)
                  (or (and (not (nil? width)) (>= width 0))
                      (and (not (nil? length)) (>= length 0))))))))

(def parcel
  [:and
   [:map
    [:mode
     {:optional true :default "test"}
     [:and non-empty-string? mode-str?]]
    [:length {:optional true} [:or float? integer?]]
    [:width {:optional true} [:or float? integer?]]
    [:height {:optional true} [:or float? integer?]]
    [:weight [:or float? integer?]]
    [:predefined_package {:optional true} non-empty-string?]]
   [:fn {:error/message
         "if one dimension (l,w,h) is supplied, all three must be supplied"}
    dim-checker]])

(def parcel-request
  [:map [:parcel parcel]])

(defn validate-parcel [parcel-map]
  (get-validated-map parcel-request parcel-map))

;; Insurance
(def ins-id-prefixed?
  (m/-simple-schema
   {:type :user/ins-id_prefixed
    :pred #(= "ins_" (subs % 0 4))
    :type-properties {:error/message "id must be prefixed with ins_"
                      :json-schema/type "string"}}))

(def insurance
  [:map
   [:mode
    {:optional true :default "test"}
    [:and non-empty-string? mode-str?]]
   [:to_address address]
   [:from_address address]
   [:carrier non-empty-string?]
   [:reference {:optional true} non-empty-string?]
   [:amount {:optional true} non-empty-string?]
   [:provider {:optional true} non-empty-string?]
   [:provider-id {:optional true} non-empty-string?]
   [:shipment_id {:optional true} non-empty-string?]
   [:tracking_code {:optional true} non-empty-string?]
   ])

(def insurance-request
  [:map [:insurance insurance]])

(defn validate-insurance [insurance-map]
  (get-validated-map insurance-request insurance-map))

(def fee
  [:map
   [:type non-empty-string?]
   [:amount non-empty-string?]
   [:charged {:optional true :default false} boolean?]
   [:refunded {:optional true :default false} boolean?]
   ])

(def tracker
  [:map
   [:mode
    {:optional true :default "test"}
    [:and non-empty-string? mode-str?]]
   [:tracking_code non-empty-string?]
   [:status {:optional true} non-empty-string?]
   [:signed_by {:optional true} non-empty-string?]
   [:weight {:optional true} float?]
   [:estimated_delivery_date {:optional true} iso-date-string?]
   [:shipment_id {:optional true} non-empty-string?]
   [:carrier {:optional true} non-empty-string?]
   [:public_url {:optional true} non-empty-string?]
   [:fees {:optional true} fee]
   ])

(def tracker-request
  [:map
   [:tracker tracker]])

(defn validate-tracker [tracker-map]
  (get-validated-map tracker-request tracker-map))


;; Shipment components
;; Options
(def cod-methods ["CASH", "CHECK", "MONEY_ORDER"])
(def cod-method?
  (str-vec-schema "cod-method" cod-methods))

(def endorsement-opts ["ADDRESS_SERVICE_REQUESTED"
                       "FORWARDING_SERVICE_REQUESTED"
                       "CHANGE_SERVICE_REQUESTED"
                       "RETURN_SERVICE_REQUESTED"
                       "LEAVE_IF_NO_RESPONSE"])
(def endorsement?
  (str-vec-schema "endorsement" endorsement-opts))

(def hazmat-opts ["PRIMARY_CONTAINED"
                  "PRIMARY_PACKED"
                  "PRIMARY"
                  "SECONDARY_CONTAINED"
                  "SECONDARY_PACKED"
                  "SECONDARY"
                  "ORMD"
                  "LIMITED_QUANTITY"
                  "LITHIUM"])
(def hazmat?
  (str-vec-schema "hazmat" hazmat-opts))

(def incoterms ["EXW"
                "FCA"
                "CPT"
                "CIP"
                "DAT"
                "DAP"
                "DDP"
                "FAS"
                "FOB"
                "CFR"
                "CIF"])

(def incoterm?
  (str-vec-schema "incoterm" incoterms))

(def label-formats ["PNG"
                    "PDF"
                    "ZPL"
                    "EPL2"])
(def label-format?
  (str-vec-schema "label-format" label-formats))

(def payment-types ["SENDER"
                    "RECEIVER"
                    "COLLECT"
                    "THIRD_PARTY"])
(def payment-type?
  (str-vec-schema "payment-type" payment-types))

(def options
  [:map
   [:additional_handling  {:optional true} boolean?]
   [:address_validation_level {:optional true} non-empty-string?] ; for USPS only
   [:alcohol {:optional true} boolean?]
   [:bill_receiver_account {:optional true} non-empty-string?]
   [:bill_receiver_postal_code {:optional true} non-empty-string?]
   [:bill_third_party_account {:optional true} non-empty-string?]
   [:bill_third_party_country {:optional true} non-empty-string?]
   [:bill_third_party_postal_code {:optional true} non-empty-string?]
   [:by_drone {:optional true} boolean?] ; look at you, fancy
   [:carbon_neutral {:optional true} boolean?] ; pay extra for greenwashing
   [:cod_amount {:optional true} non-empty-string?]
   [:cod_method {:optional true} [:and non-empty-string? cod-method?]]
   [:cod_address_id {:optional true} non-empty-string?]
   [:currency {:optional true} non-empty-string?]
   [:delivery_confirmation {:optional true} non-empty-string?]
   [:dropoff_type {:optional true} non-empty-string?]
   [:dry_ice {:optional true} boolean?]
   [:dry_ice_medical {:optional true} boolean?]
   [:dry_ice_weight {:optional true} non-empty-string?]
   [:endorsement {:optional true} [:and non-empty-string? endorsement?]]
   [:freight_charge {:optional true} non-empty-string?]
   [:handling_instructions  {:optional true} non-empty-string?]
   [:hazmat {:optional true} [:and non-empty-string? hazmat?]]
   [:hold_for_pickup {:optional true} boolean?]
   [:incoterm {:optional true} [:and non-empty-string? incoterm?]]
   [:invoice_number {:optional true} non-empty-string?]
   [:label_date {:optional true} [:and non-empty-string? iso-date-string?]]
   [:label_format {:optional true} [:and non-empty-string? label-format?]]
   [:machinable {:optional true} boolean?]
   [:payment {:optional true}
    [:map
     [:type payment-type?]
     [:account non-empty-string?]
     [:country [:and non-empty-string? iso-country-code?]]
     [:postal-code non-empty-string?]]]
   [:print_custom_1 {:optional true} non-empty-string?]
   [:print_custom_2 {:optional true} non-empty-string?]
   [:print_custom_3 {:optional true} non-empty-string?]
   [:print_custom_1_barcode {:optional true} boolean?]
   [:print_custom_2_barcode {:optional true} boolean?]
   [:print_custom_3_barcode {:optional true} boolean?]
   [:print_custom_1_code {:optional true} non-empty-string?]
   [:print_custom_2_code {:optional true} non-empty-string?]
   [:print_custom_3_code {:optional true} non-empty-string?]
   [:saturday_delivery {:optional true} boolean?]
   [:special_rates_eligibility {:optional true} non-empty-string?]
   [:smarpost_hub {:optional true} non-empty-string?]
   [:smartpost_manifest {:optional true} non-empty-string?]
   [:billing_ref {:optional true} non-empty-string?]
   [:certified_mail {:optional true} boolean?]
   [:registered_mail {:optional true} boolean?]
   [:registered_mail_amount {:optional true} double?]
   [:return_receipt {:optional true} boolean?]])

(def customs-item
  [:map
   [:description non-empty-string?]
   [:quantity float?]
   [:value float?]
   [:weight {:optional true} float?]
   [:hs_tariff_number {:optional true} non-empty-string?]
   [:code {:optional true} non-empty-string?]
   [:origin_country {:optional true} non-empty-string?]
   [:currency {:optional true} non-empty-string?]])

(def customs-info
  [:map
   [:eel_pfc {:optional true} non-empty-string?]
   [:contents_type {:optional true} non-empty-string?]
   [:contents_explanation {:optional true} non-empty-string?]
   [:customs_certify {:optional true} boolean?]
   [:customs_signer {:optional true} non-empty-string?]
   [:non_delivery_option {:optional true} non-empty-string?]
   [:restriction_type {:optional true} non-empty-string?]
   [:restriction_comments {:optional true} non-empty-string?]
   [:customs_items customs-item]
   [:declaration {:optional true} non-empty-string?]])

(def tax-identifier
  [:map
   [:entity non-empty-string?]
   [:tax_id non-empty-string?]
   [:tax_id_type non-empty-string?]
   [:issuing_country non-empty-string?]])

;; Shipment
(def shipment
  [:map
   [:reference {:optional true} non-empty-string?]
   [:mode
    {:optional true :default "test"}
    [:and non-empty-string? mode-str?]]
   [:to_address address]
   [:from_address address]
   [:return_address {:optional true} address]
   [:buyer_address {:optional true} address]
   [:parcel parcel]
   [:customs_info {:optional true} customs-info]
   [:insurance {:optional true} non-empty-string?]
   [:options {:optional true} options]
   [:is_return {:optional true} boolean?]
   [:service {:optional true} non-empty-string?]
   [:carrier_accounts {:optional true} [:vector non-empty-string?]]
   [:carbon_offset {:optional true} boolean?]
   [:tax_identifiers {:optional true} [:vector tax-identifier]]])

(def shipment-request
  [:map
   [:shipment shipment]])

(defn validate-shipment [shipment-map]
  (get-validated-map shipment-request shipment-map))

(def rate
  [:map
   [:id non-empty-string?]
   [:object non-empty-string?]
   [:mode non-empty-string?]
   [:service non-empty-string?]
   [:carrier non-empty-string?]
   [:carrier_account_id non-empty-string?]
   [:shipment_id non-empty-string?]
   [:rate non-empty-string?]
   [:currency {:optional true} non-empty-string?]
   [:retail_rate {:optional true} non-empty-string?]
   [:retail_currency {:optional true} non-empty-string?]
   [:list_rate {:optional true} non-empty-string?]
   [:list_currency {:optional true} non-empty-string?]
   [:delivery_days {:optional true} int?]
   [:delivery_date {:optional true}
    [:or [:and non-empty-string? iso-date-string?] nil?]]
   [:delivery_date_guaranteed boolean?]
   [:est_delivery_days {:optional true} int?]
   [:billing_type {:optional true} non-empty-string?]
   [:carbon_offset {:optional true} :map]])


(def shipment-buy
  [:map
   [:rate rate]
   [:insurance {:optional true} double?]])

(defn validate-shipment-buy [buy-map]
  (get-validated-map shipment-buy buy-map))
