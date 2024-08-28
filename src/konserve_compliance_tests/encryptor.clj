(ns konserve-compliance-tests.encryptor
  (:require [clojure.core.async :refer [go <!]]
            [konserve.compliance-test :refer [compliance-test async-compliance-test]]
            [superv.async :refer [<?-]]))

(defn async-encryptor-test
  [store-name create-store delete-store-async]
  (go
    (<! (delete-store-async store-name))
    (let [config {:encryptor {:type :aes :key "s3cr3t"}}
          store  (<?- (create-store store-name :config config))]
      (<! (async-compliance-test store))
      (<! (delete-store-async store-name)))))

(defn sync-encryptor-test
  [store-name create-store delete-store]
  (delete-store store-name)
  (let [store (create-store store-name
                            :config {:encryptor {:type :aes
                                                 :key  "s3cr3t"}}
                            :opts {:sync? true})]
    (compliance-test store)
    (delete-store store-name)))