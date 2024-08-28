(ns konserve-compliance-tests.serializers
  (:require [clojure.core.async :as a :refer [<! go]]
            [clojure.test :refer [is testing]]
            [fress.api :as fress]
            [konserve.core :as k]
            [konserve.serializers :refer [fressian-serializer]]
            [incognito.base :refer [IncognitoTaggedLiteral]]))

(deftype MyType [a b]
  IEquiv
  (-equiv [this other]
    (and (instance? MyType other)
         (= a (.-a ^MyType other))
         (= b (.-b ^MyType other)))))

(defrecord MyRecord [a b])

(def custom-read-handlers
  {"my-type"
   (fn [rdr _ _]
       (MyType. (fress.api/read-object rdr)
                (fress.api/read-object rdr)))
   "custom-tag"
   (fn [rdr _ _]
     (doto (js/Date.) (.setTime (fress/read-object rdr))))
   "my-record"
   (fn [rdr _ _]
     (MyRecord. (fress/read-object rdr)
                (fress/read-object rdr)))})

(def custom-write-handlers
  {js/Date
   {"custom-tag"
    (fn [wrt date]
      (fress/write-tag wrt "custom-tag" 1)
      (fress/write-object wrt (.getTime date)))}
   MyType {"my-type"
           (fn [writer o]
             (fress/write-tag writer "my-type" 2)
             (fress/write-object writer (.-a o))
             (fress/write-object writer (.-b o)))}
   MyRecord {"my-record"
             (fn [writer o]
               (fress/write-tag writer "my-record" 2)
               (fress/write-object writer (.-a o))
               (fress/write-object writer (.-b o)))}})

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
          (is (= my-record (<! (k/get-in store [:foo]))))
          (when (.-close (:backing store))
            (<! (.close (:backing store))))
          (<! (delete-store-async store-name)))))))

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
              d (js/Date.)
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
                    (is (= d (<! (k/get-in store [:foo]))
