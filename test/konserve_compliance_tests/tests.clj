(ns konserve-compliance-tests.tests
  (:refer-clojure :exclude [keys])
  (:require [clojure.test :refer [deftest]]
            [clojure.core.async :refer [<!! go] :as async]
            [konserve.core :refer [bassoc bget keys]]
            [konserve.compliance-test :refer [compliance-test]]
            [konserve.filestore :refer [connect-fs-store delete-store]]
            [konserve-compliance-tests.cache :as ct]
            [konserve-compliance-tests.encryptor :as et]
            [konserve-compliance-tests.gc :as gct]
            [konserve-compliance-tests.serializers :as st]))

#!============
#! Cache tests

(deftest cache-PEDNKeyValueStore-test
  (delete-store "/tmp/cache-store")
  (let [store (connect-fs-store "/tmp/cache-store" :opts {:sync? true})]
    (<!! (ct/test-cached-PEDNKeyValueStore-async store))))

(deftest cache-PKeyIterable-test
  (delete-store "/tmp/cache-store")
  (let [store (connect-fs-store "/tmp/cache-store" :opts {:sync? true})]
    (<!! (ct/test-cached-PKeyIterable-async store))))

(deftest cache-PBin-test
  (delete-store "/tmp/cache-store")
  (let [store (connect-fs-store "/tmp/cache-store" :opts {:sync? true})
        f (fn [{:keys [input-stream]}]
            (async/to-chan! [input-stream]))]
    (<!! (ct/test-cached-PBin-async store f))))

#!============
#! GC tests

(deftest async-gc-test
  (delete-store "/tmp/gc-store")
  (let [store (connect-fs-store "/tmp/gc-store" :opts {:sync? true})]
    (<!! (gct/test-gc-async store))))

#!==================
#! Serializers tests

(deftest fressian-serializer-test
  (<!! (st/test-fressian-serializers-async "/tmp/serializers-test"
                                           connect-fs-store
                                           (fn [p] (go (delete-store p)))
                                           (fn [{:keys [input-stream]}]
                                             (async/to-chan! [input-stream])))))

(deftest CBOR-serializer-test
  (st/cbor-serializer-test "/tmp/konserve-fs-cbor-test"
                           connect-fs-store
                           (fn [p] (go (delete-store p)))))

#!==================
#! Encryptor tests

(deftest encryptor-sync-test
  (et/sync-encryptor-test "/tmp/encryptor-test"
                          connect-fs-store
                          delete-store))

(deftest encryptor-async-test
  (<!! (et/async-encryptor-test "/tmp/encryptor-test"
                                connect-fs-store
                                (fn [p] (go (delete-store p))))))
