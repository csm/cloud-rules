(ns cloud.rules.crypto)

(def master-secret
  (memoize
    (fn []
      )))

(defn decrypt-kek
  [encrypted-kek])