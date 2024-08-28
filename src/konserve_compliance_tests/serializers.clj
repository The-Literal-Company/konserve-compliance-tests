(ns konserve-compliance-tests.serializers
  (:require [clojure.core.async :as a :refer [<! go <!!]]
            [clojure.test :refer [is testing]]
            [fress.api :as fress]
            [konserve.core :as k]
            [konserve.serializers :refer [fressian-serializer]]
            [incognito.base])
  (:import [org.fressian.handlers WriteHandler ReadHandler]
           [incognito.base IncognitoTaggedLiteral]
           (java.util Date)))

(deftype MyType [a b]
  Object
  (equals [this other]
    (and (instance? MyType other)
         (= a (.a ^MyType other))
         (= b (.b ^MyType other))))
  (hashCode [this] (hash [a b])))

(defrecord MyRecord [a b])

(def custom-read-handlers
  {"my-type"
   (reify ReadHandler
     (read [_ rdr _ _]
       (MyType. (fress/read-object rdr)
                (fress/read-object rdr))))
   "custom-tag"
   (reify ReadHandler
     (read [_ rdr _ _]
       (Date. ^long (.readObject rdr))))
   "my-record"
   (reify ReadHandler
     (read [_ rdr _ _]
       (MyRecord. (fress/read-object rdr)
                  (fress/read-object rdr))))})

(def custom-write-handlers
  {Date
   {"custom-tag"
    (reify WriteHandler
      (write [_ writer instant]
        (fress/write-tag writer "custom-tag" 1)
        (fress/write-object writer (.getTime ^Date instant))))}
   MyType {"my-type"
           (reify WriteHandler
             (write [_ writer o]
               (fress/write-tag writer "my-type" 2)
               (fress/write-object writer ^MyType (.-a o))
               (fress/write-object writer ^MyType (.-b o))))}
   MyRecord {"my-record"
             (reify WriteHandler
               (write [_ writer o]
                 (fress/write-tag writer "my-record" 2)
                 (fress/write-object writer (.-a o))
                 (fress/write-object writer (.-b o))))}})

(defn- test-fressian-incognito-record-recovery
  [store-name connect-store delete-store-async locked-cb]
  (go
    (testing ":read-handlers arg to connect-store let's us recover records"
      (let [read-handlers {'konserve_compliance_tests.serializers.MyRecord map->MyRecord}
            _ (<! (delete-store-async store-name))
            store (<! (connect-store store-name :read-handlers (atom read-handlers)))
            my-record (map->MyRecord {:a 0 :b 1})]
        (and
         (is (nil? (<! (k/get-in store [:foo]))))
         (is (= [nil my-record] (<! (k/assoc-in store [:foo] my-record))))
         (testing "written as 'irecord'"
           (let [bytes (<! (k/bget store :foo locked-cb))
                 o (fress/read bytes)]
             (and (is (fress/tagged-object? o))
                  (is (= "irecord" (fress/tag o))))))
         (is (= my-record (<! (k/get-in store [:foo])))))
        (<! (delete-store-async store-name))))))

(defn test-fressian-serializers-async
  "Test roundtripping custom types and records using the :FressianSerializer.
   `locked-cb` is used to verify fressian bytes, needs to simply pass through
   the input-stream on jvm or a full realized byte array in js"
  [store-name connect-store delete-store-async locked-cb]
  (go
    (and
     (testing ":serializers arg to connect-store"
       (let [serializers {:FressianSerializer (fressian-serializer custom-read-handlers
                                                                   custom-write-handlers)}
             _ (assert (nil? (<! (delete-store-async store-name))))
             store (<! (connect-store store-name :serializers serializers))
             d (Date.)
             my-type (MyType. "a" "b")
             my-record (map->MyRecord {:a 0 :b 1})
             res (and
                  (is [nil 42] (<! (k/assoc-in store [:foo] 42)))
                  (is (= 42 (<! (k/get-in store [:foo]))))
                  (is (= [nil d] (<! (k/assoc-in store [:foo] d))))
                  (testing "should be written with custom-tag not built-in"
                    (let [bytes (<! (k/bget store :foo locked-cb))
                          o (fress/read bytes)]
                      (and (is (fress/tagged-object? o))
                           (is (= "custom-tag" (fress/tag o))))))
                  (is (= d (<! (k/get-in store  [:foo]))))
                  (is (= [nil my-type] (<! (k/assoc-in store [:foo] my-type))))
                  (is (= my-type (<! (k/get-in store [:foo]))))
                  (testing "custom write-handler takes precedent over incognito"
                    (and
                     (is (= [nil my-record] (<! (k/assoc-in store [:foo] my-record))))
                     (let [bytes (<! (k/bget store :foo locked-cb))
                           o (fress/read bytes)]
                       (and (is (fress/tagged-object? o))
                            (is (= "my-record" (fress/tag o)))))
                     (is (= my-record (<! (k/get-in store [:foo])))))))]
         res))
     (testing "records are intercepted by incognito by default"
       (let [_ (<! (delete-store-async store-name))
             store (<! (connect-store store-name))
             my-record (map->MyRecord {:a 0 :b 1})]
         (and
           (is (= [nil my-record] (<! (k/assoc-in store [:bar] my-record))))
           (testing "written as 'irecord'"
             (let [bytes (<! (k/bget store :bar locked-cb))
                   o (fress/read bytes)]
               (and (is (fress/tagged-object? o))
                    (is (= "irecord" (fress/tag o))))))
           (is (instance? IncognitoTaggedLiteral (<! (k/get-in store [:bar])))))))
     (<! (test-fressian-incognito-record-recovery store-name connect-store delete-store-async locked-cb)))))

(defn cbor-serializer-test [store-name connect-store delete-store-async]
  (testing "Test CBOR serializer functionality."
    (let [_ (<!! (delete-store-async store-name))
          store (<!! (connect-store store-name :default-serializer :CBORSerializer))]
      (is (nil? (<!! (k/get-in store [:foo]))))
      (<!! (k/assoc-in store [:foo] (Date.)))
      (is (= java.time.Instant (type (<!! (k/get-in store [:foo])))))
      (<!! (k/dissoc store :foo))
      (is (nil? (<!! (k/get-in store [:foo]))))
      (<!! (delete-store-async store-name)))))