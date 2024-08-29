(ns konserve-compliance-tests.core
  (:require [clojure.core.async :refer [<!! <! go]]
            [clojure.test :refer [are is testing]]
            [konserve.core :as k]))

(declare sync-core-api-test
         async-core-api-test
         sync-append-store-test)

(defn compliance-test [store]
  (and
    (sync-core-api-test store)
    (<!! (async-core-api-test store))
    (sync-append-store-test store)))

#!=============================================================================

(defn sync-core-api-test [store]
  (let [opts {:sync? true}]
    (testing "Test the core API."
      (and
        (is (nil? (k/get store :foo nil opts)))
        (is (false? (k/exists? store :foo opts)))
        (is (some? (k/assoc store :foo :bar opts)))
        (is (some? (k/exists? store :foo opts)))
        (is (= :bar (k/get store :foo nil opts)))
        (is (some? (k/assoc-in store [:foo] :bar2 opts)))
        (is (= :bar2 (k/get store :foo nil opts)))
        (is (= :default (k/get-in store [:fuu] :default opts)))
        (is (= :bar2 (k/get store :foo nil opts)))
        (is (= :default (k/get-in store [:fuu] :default opts)))
        (is (some? (k/update-in store [:foo] name opts)))
        (is (= "bar2" (k/get store :foo nil opts)))
        (is (some? (k/assoc-in store [:baz] {:bar 42} opts)))
        (is (= 42 (k/get-in store [:baz :bar] nil opts)))
        (is (k/update-in store [:baz :bar] inc opts))
        (is (= 43 (k/get-in store [:baz :bar] nil opts)))
        (is (some? (k/update-in store [:baz :bar] (fn [x] (+ x 2 3)) opts)))
        (is (= 48 (k/get-in store [:baz :bar] nil opts)))
        (is (true? (k/dissoc store :foo opts)))
        (is (false? (k/dissoc store :not-there opts)))
        (is (nil? (k/get-in store [:foo] nil opts)))
        (is (some? (k/bassoc store :binbar (byte-array (range 10)) opts)))
        (is (some? (k/bget store :binbar (fn [{:keys [input-stream]}]
                                           (go
                                             (is (= (map byte (slurp input-stream))
                                                    (range 10)))
                                             true))
                           opts)))
        (let [store-keys (k/keys store opts)
              #_#_keys-by-key (into {} (map (fn [m] [(:key m) m])) store-keys)]
          (and
            (is (= 2 (count store-keys)))
            (is (every? inst? (map :last-write store-keys)))
            (is [:baz :binbar] (mapv :key (sort-by :last-write store-keys)))
            (is [:edn :binary] (mapv :type (sort-by :last-write store-keys)))))
        (doseq [to-delete [:baz :binbar :foolog :baz :foo]]
          (k/dissoc store to-delete opts))))))

#! TODO parity with sync-compliance-test
(defn async-core-api-test [store]
  (testing "test core api async"
    (go
      (and
        (is (= nil (<! (k/get store :foo))))
        (is (= [nil :bar] (<! (k/assoc store :foo :bar))))
        (is (= :bar (<! (k/get store :foo))))
        (is (= [nil :bar2] (<! (k/assoc-in store [:foo] :bar2))))
        (is (= :bar2 (<! (k/get store :foo))))
        (is (= :default (<! (k/get-in store [:fuu] :default))))
        (<! (k/update-in store [:foo] name))
        (is (= "bar2" (<! (k/get store :foo))))
        (<! (k/assoc-in store [:baz] {:bar 42}))
        (is (= (<! (k/get-in store [:baz :bar])) 42))
        (<! (k/update-in store [:baz :bar] inc))
        (is (= (<! (k/get-in store [:baz :bar])) 43))
        (<! (k/update-in store [:baz :bar] #(+ % 2 3)))
        (is (= (<! (k/get-in store [:baz :bar])) 48))
        (<! (k/dissoc store :foo))
        (is (= (<! (k/get-in store [:foo])) nil))))))

(defn sync-append-store-test [store]
  (let [opts {:sync? true}]
    (testing "Testing the append store functionality."
      (and
        (is (some? (k/append store :foolog {:bar 42} opts)))
        (is (some? (k/append store :foolog {:bar 43} opts)))
        (is (= (k/log store :foolog opts)
               '({:bar 42}
                 {:bar 43})))
        (is (= [{:bar 42} {:bar 43}]
               (k/reduce-log store
                             :foolog
                             (fn [acc elem]
                               (conj acc elem))
                             []
                             opts)))
        (let [{:keys [key type last-write]} (k/get-meta store :foolog nil opts)]
          (are [x y] (= x y)
                     :foolog key
                     :append-log type
                     java.util.Date (clojure.core/type last-write)))))))
