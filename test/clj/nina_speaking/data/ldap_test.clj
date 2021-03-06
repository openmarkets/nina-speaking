(ns nina-speaking.data.ldap-test
  (:require [clojure.test :refer :all]
            [clojure.test.check :as check]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :refer [defspec]]

            [taoensso.timbre :as log]

            [com.stuartsierra.component :as component]
            [nina-speaking.test-support.storage :refer :all]

            [nina-speaking.data.ldap :refer :all]

            ;; TODO: don’t forget to remove me!
            [clj-ldap.client :as ldap]))

(deftest cleaning-up-after-ldap-tests
  (let [host                           "ldap"
        dn                             "cn=admin,dc=thetripps,dc=org"
        password                       "omelet-sever-exposure-averse"
        {:keys [connection] :as store} (component/start (new-storage host dn password))
        base-dn                        "ou=people,dc=thetripps,dc=org"]
    (try
      (init-store store)

      (letfn [(children [dn]
                (log/spy (search store "(objectclass=*)" dn
                                 {:attributes [:cn] :scope :subordinate})))
              (children? [dn]
                (< 0 (log/spy (count (children dn)))))]

        (is (log/spy (children? base-dn)))

        (doall
         (for [{:keys [dn]} (children base-dn)
               :when        (not (children? dn))]
           (do
             (is (not (children? dn)))
             (log/debugf "DELETE (%s): %s" base-dn dn)
             (ldap/delete connection dn)))))

      (is (= [] (filter identity (map :cn (all-people store)))))

      (finally (component/stop store)))))

(deftest creating-ldap-records
  (with-storage
    (fn [store]
      (let [joe  {:objectClass #{"organizationalPerson" "inetOrgPerson"
                                 "dcObject" "top"}
                  :cn          "jdoe"
                  :dc          "people"
                  :sn          "Doe"
                  :mail        "john.doe@provider.com"}
            jane {:objectClass #{"organizationalPerson" "inetOrgPerson"
                                 "dcObject" "top"}
                  :cn          "jsmith"
                  :dc          "people"
                  :sn          "Smith"
                  :mail        "jane.smith@supplier.com"}]
        (add-records store
                     {"cn=jdoe,ou=providers,ou=people,dc=thetripps,dc=org"   joe
                      "cn=jsmith,ou=suppliers,ou=people,dc=thetripps,dc=org" jane})

        (is (= ["jdoe" "jsmith"] (filter identity (map :cn (all-people store)))))
        ))))

(deftest adding-a-new-role
  (with-storage
    (fn [store]
      (add-role store "cheeky-monkey")
      (is (= [{:ou "cheeky-monkey"
               :dn "ou=cheeky-monkey,ou=people,dc=thetripps,dc=org"}]
             (search store "ou=cheeky-monkey"))))))

(deftest adding-a-new-role
  (with-storage
    (fn [store]
      (add-role store "0")
      (is (= [{:ou "0"
               :dn "ou=0,ou=people,dc=thetripps,dc=org"}]
             (search store "ou=0"))))))


(defspec upserting-new-roles
  50
  (prop/for-all [role (gen/not-empty gen/string-alphanumeric)]
                (with-storage
                  (fn [store]
                    (add-role store role)
                    (let [rdn (str "ou=" role)]
                      (= [{:ou role
                           :dn (format"ou=%s,ou=people,dc=thetripps,dc=org" role)}]
                         (log/spy (search store (log/spy rdn)))))))))

(deftest adding-a-person-given-attributes
  (with-storage
    (fn [store]
      (add-person store
                  {:email    "toby@tripp.net"
                   :role     "code-monkey"
                   :password "angry-monkeys-code"})
      (is (= {:cn   "toby"
              :dc   "ou=people"
              :sn   "Unknown"
              :dn   "cn=toby,ou=code-monkey,ou=people,dc=thetripps,dc=org"
              :mail "toby@tripp.net"}
             (by-email store "toby@tripp.net")))

      (is (= (add-person store
                         {:email    "thomas@tripp.net"
                          :role     "code-monkey"
                          :password "angry-monkeys-code"})
             {:cn   "thomas"
              :dc   "ou=people"
              :sn   "Unknown"
              :dn   "cn=thomas,ou=code-monkey,ou=people,dc=thetripps,dc=org"
              :mail "thomas@tripp.net"}))
      )))

(deftest finding-people-by-email
  (with-storage
    (fn [store]
      (let [joe {:objectClass #{"organizationalPerson" "inetOrgPerson"
                                "dcObject" "top"}
                 :cn          "jdoe"
                 :dc          "people"
                 :sn          "Doe"
                 :mail        "john.doe@provider.com"}]
        (add-records store
                     {"cn=jdoe,ou=providers,ou=people,dc=thetripps,dc=org" joe})

        (is (= {:cn   "jdoe"
                :dc   "people"
                :dn   "cn=jdoe,ou=providers,ou=people,dc=thetripps,dc=org"
                :sn   "Doe"
                :mail "john.doe@provider.com"}
               (by-email store "john.doe@provider.com")))))))

(comment
  (run-tests 'nina-speaking.data.ldap-test)

  (gen/sample (gen/not-empty gen/string-alphanumeric))

  (with-storage
    (fn [store]
      (search store
              "(objectclass=*)"
              "ou=code-monkey,ou=people,dc=thetripps,dc=org"
              {:attributes [:cn]
               :scope :subordinate})))

  (> 1 0)
  )
