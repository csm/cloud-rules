(ns cloud.rules
  (:require [datomic.client.api :as d]
            [datomic.ion :as ion]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [cloud.rules.crypto :as crypto]
            [cloud.rules.schema :as schema]
            [clojure.string :as string])
  (:import [jakarta.mail Session Folder Message Flags$Flag]
           (java.util Properties)
           (com.sun.mail.imap IMAPStore IMAPFolder)
           (jakarta.mail.internet InternetAddress)))

(def datomic
  (memoize
    (fn []
      (let [client (d/client (edn/read-string (slurp (io/resource "datomic/ion/config.edn"))))]
        (when-not (some #(= % "cloud-rules") (d/list-databases client {}))
          (d/create-database client {:db-name "cloud-rules"})
          (d/transact (d/connect client {:db-name "cloud-rules"})
                      {:tx-data schema/schema-0}))
        (let [conn (d/connect client {:db-name "cloud-rules"})
              [vid current-version] (or (first (d/q {:query '[:find ?e ?v :in $ :where [?e :schema/version ?v]]
                                                     :args [(d/db conn)]}))
                                        ["init-schema-version" 0])]
          (when (= current-version 0)
            (d/transact conn {:tx-data schema/schema-1})
            (d/transact conn {:tx-data [[:db/add vid :schema/version 1]]}))
          [client conn])))))

(defn datomic-client [] (first (datomic)))
(defn datomic-conn [] (second (datomic)))

(defn match-rule
  [rule ^Message message]
  (let [op (case (-> rule :rule/operation :db/ident)
             :ruleOp/equals =
             :ruleOp/contains string/includes?
             :ruleOp/startsWith string/starts-with?
             :ruleOp/endsWith string/ends-with?)
        xf (if (:rule/ignore-case rule)
             string/lower-case
             identity)
        fields (case (-> rule :rule/field :db/ident)
                 :ruleField/recipients (map #(.getAddress ^InternetAddress %) (.getAllRecipients message))
                 :ruleField/sender (map #(.getAddress ^InternetAddress %) (.getFrom message))
                 :ruleField/subject [(.getSubject message)])
        pattern (xf (:rule/pattern rule))]
    (when (some #(op (xf %) pattern) fields)
      (case (-> rule :rule/action :db/ident)
        :ruleAction/move [(:rule/target rule) message]
        :ruleAction/delete [:delete message]))))

(defn run
  [& args]
  (let [conn (datomic-conn)]
    (loop [offset 0]
      (when-let [datoms (not-empty (d/datoms (d/db conn) {:index :aevt :components [:account/id] :offset offset :limit 100}))]
        (loop [[datom & datoms] datoms]
          (when datom
            (let [account (d/pull (d/db conn) {:eid (:e datom)
                                               :selector '[*
                                                           {:account/rules [*]}]})
                  account (crypto/decrypt-data account)
                  session (Session/getInstance (Properties.))
                  store (.getStore session "imaps")]
              (.connect store (:account/hostname account)
                        (:account/port account)
                        (:account/username account)
                        (:account/password account))
              (let [folder (doto (.getFolder store ^String (:account/inbox account))
                             (.open Folder/READ_WRITE))
                    messages (if-let [uid (:account/last-uid account)]
                               (.getMessagesByUID ^IMAPFolder folder (inc uid) IMAPFolder/LASTUID)
                               (.getMessages folder))
                    ops (loop [ops {}
                               messages messages]
                          (if-let [[message & messages] messages]
                            (recur (loop [rules (:account/rules account)]
                                     (if-let [[rule & rules] rules]
                                       (if-let [[k msg] (match-rule rule message)]
                                         (update ops k conj msg)
                                         (recur rules))
                                       ops))
                                   messages)
                            ops))
                    last-uid (.getUID ^IMAPFolder folder (last messages))]
                (doseq [[k msgs] ops]
                  (if (= :delete k)
                    (do (doseq [msg msgs]
                          (.setFlag msg Flags$Flag/DELETED true))
                        (.expunge msgs)))
                  (let [target-folder (doto (.getFolder store ^String k)
                                        (.open Folder/READ_WRITE))]
                    (.moveMessages ^IMAPFolder folder msgs target-folder)))
                (d/transact conn [[:db/add (:db/id account) :account/last-uid last-uid]])))
            (recur datoms)))
        (recur (+ offset (count datoms))))))
  "fetch accounts from datomic"
  "for each account"
  "  pull the account info and rules"
  "  connect to the mail server"
  "  fetch new messages from last-uid on"
  "  match each message fetched"
  "  operate on matched messages"
  "  store last-uid of last message processed")