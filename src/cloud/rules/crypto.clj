(ns cloud.rules.crypto
  (:require [buddy.core.crypto :refer [decrypt encrypt]]
            [clojure.string :as string]
            [buddy.core.nonce :as nonce])
  (:import (software.amazon.awssdk.services.ssm SsmClient)
           (software.amazon.awssdk.services.ssm.model GetParameterRequest)
           (java.util Base64)))

(def master-secret
  (memoize
    (fn []
      (let [ssm (SsmClient/create)]
        (-> (.getParameter ssm ^GetParameterRequest (-> (GetParameterRequest/builder)
                                                        (.name "/cloud/rules/masterSecret")
                                                        (.withDecryption true)
                                                        (.build)))
            (.parameter)
            (.value)
            (->> (.decode (Base64/getDecoder))))))))

(defn decrypt-value
  [key value]
  (let [[iv ct] (map #(.decode (Base64/getDecoder) ^String %)
                   (string/split value #":"))]
   (decrypt ct key iv {:alg :aes256-gcm})))


(defn decrypt-kek
  [encrypted-kek]
  (decrypt-value (master-secret) encrypted-kek))

(defn new-kek
  []
  (let [kek (nonce/random-bytes 32)
        iv (nonce/random-bytes 12)
        ct (encrypt kek (master-secret) iv {:alg :aes256-gcm})]
    (str (.encodeToString (Base64/getEncoder) ^bytes iv) \:
         (.encodeToString (Base64/getEncoder) ^bytes ct))))

(defn decrypt-data
  [{:keys [account/kek] :as data}]
  (let [kek (decrypt-kek kek)
        dec (partial decrypt-value kek)]
    (-> (update data :account/username dec)
        (update data :account/password dec)
        (update data :account/hostname dec)
        (update data :account/port dec)
        (update data :account/port #(Integer/parseInt %)))))