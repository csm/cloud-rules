(ns cloud.rules.schema)

(def schema
  [{:db/ident :account/id
    :db/valueType :db.type/uuid
    :db/unique :db.unique/identity
    :db/cardinality :db.cardinality/one
    :db/doc "The account identifier."}

   {:db/ident :account/name
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The account name."}

   {:db/ident :account/kek
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The key encryption key for this account (encrypted)."}

   {:db/ident :account/username
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The account username (encrypted)."}

   {:db/ident :account/password
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The account password (encrypted)."}

   {:db/ident :account/inbox
    :db/valueType :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The inbox of this account (the mailbox to run rules on)."}

   {:db/ident :account/rules
    :db/valueType :db.type/ref
    :db/isComponent true
    :db/cardinality :db.cardinality/many
    :db/doc "The mail rules."}

   [:db/add "ruleAction/move" :db/ident :ruleAction/move]
   [:db/add "ruleAction/delete" :db/ident :ruleAction/delete]

   {:db/ident :rule/action
    :db/type :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "The rule action. One of :ruleAction/move or :ruleAction/delete."}

   [:db/add "ruleOp/equals" :db/ident :ruleOp/equals]
   [:db/add "ruleOp/contains" :db/ident :ruleOp/contains]
   [:db/add "ruleOp/startsWith" :db/ident :ruleOp/startsWith]
   [:db/add "ruleOp/endsWith" :db/ident :ruleOp/endsWith]

   {:db/ident :rule/operation
    :db/type :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "The rule operation."}

   {:db/ident :rule/ignoreCase
    :db/type :db.type/boolean
    :db/cardinality :db.cardinality/one
    :db/doc "Whether to run the rule case-insensitively."}

   [:db/add "ruleField/sender" :db/ident :ruleField/sender]
   [:db/add "ruleField/subject" :db/ident :ruleField/subject]
   [:db/add "ruleField/recipients" :db/ident :ruleField/recipients]

   {:db/ident :rule/field
    :db/type :db.type/ref
    :db/cardinality :db.cardinality/one
    :db/doc "The field to match."}

   {:db/ident :rule/pattern
    :db/type :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The pattern to match."}

   {:db/ident :rule/target
    :db/type :db.type/string
    :db/cardinality :db.cardinality/one
    :db/doc "The target mailbox name, if operation is move."}])

