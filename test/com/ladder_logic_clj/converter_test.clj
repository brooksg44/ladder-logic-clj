(ns com.ladder-logic-clj.converter-test
  (:require [clojure.test :refer [deftest is testing]]
            [com.ladder-logic-clj.parser :as parser]
            [com.ladder-logic-clj.converter :as converter]))

(deftest element-creation-test
  (testing "Creating contact elements"
    (let [contact (converter/create-contact "X1" false {:x 1 :y 1})
          negated-contact (converter/create-contact "X2" true {:x 2 :y 2})]

      (is (= "contact" (:element-type contact)))
      (is (= "contact_negated" (:element-type negated-contact)))
      (is (= "X1" (get-in contact [:properties :variable])))
      (is (= "X2" (get-in negated-contact [:properties :variable])))
      (is (= {:x 1 :y 1} (:position contact)))
      (is (= {:x 2 :y 2} (:position negated-contact)))))

  (testing "Creating coil elements"
    (let [coil (converter/create-coil "Y1" false {:x 10 :y 1})
          negated-coil (converter/create-coil "Y2" true {:x 10 :y 2})]

      (is (= "coil" (:element-type coil)))
      (is (= "coil_negated" (:element-type negated-coil)))
      (is (= "Y1" (get-in coil [:properties :variable])))
      (is (= "Y2" (get-in negated-coil [:properties :variable])))
      (is (= {:x 10 :y 1} (:position coil)))
      (is (= {:x 10 :y 2} (:position negated-coil)))))

  (testing "Creating function block elements"
    (let [and-block (converter/create-function-block
                     "and"
                     {:x 5 :y 3}
                     [{:id "in1"} {:id "in2"}]
                     [{:id "out"}]
                     {})]

      (is (= "and" (:element-type and-block)))
      (is (= {:x 5 :y 3} (:position and-block)))
      (is (= 2 (count (:inputs and-block))))
      (is (= 1 (count (:outputs and-block)))))))

(deftest il-to-ld-conversion-test
  (testing "Converting LD instructions"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)]
          ld-network (converter/il-to-ld-network instructions)]

      (is (= 1 (count (:elements ld-network))))
      (is (= "contact" (get-in ld-network [:elements 0 :element-type])))
      (is (= "X1" (get-in ld-network [:elements 0 :properties :variable])))))

  (testing "Converting ST instructions"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)]

      (is (= 2 (count (:elements ld-network))))
      (is (= "contact" (get-in ld-network [:elements 0 :element-type])))
      (is (= "coil" (get-in ld-network [:elements 1 :element-type])))
      (is (= "X1" (get-in ld-network [:elements 0 :properties :variable])))
      (is (= "Y1" (get-in ld-network [:elements 1 :properties :variable])))

      ;; Check connection from contact to coil
      (is (= 1 (count (:connections ld-network))))))

  (testing "Converting AND and OR instructions"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "AND" "X2" nil)
                        (parser/->ILInstruction "OR" "X3" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)]

      (is (= 7 (count (:elements ld-network))))

      ;; Check correct element types
      (is (= 2 (count (filter #(= (:element-type %) "contact") (:elements ld-network)))))
      (is (= 1 (count (filter #(= (:element-type %) "and") (:elements ld-network)))))
      (is (= 1 (count (filter #(= (:element-type %) "or") (:elements ld-network)))))
      (is (= 1 (count (filter #(= (:element-type %) "coil") (:elements ld-network)))))))

  (testing "Converting negated instructions"
    (let [instructions [(parser/->ILInstruction "LDN" "X1" nil)
                        (parser/->ILInstruction "ANDN" "X2" nil)
                        (parser/->ILInstruction "STN" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)]

      (is (= 4 (count (:elements ld-network))))

      ;; Check correct element types
      (is (= 1 (count (filter #(= (:element-type %) "contact_negated") (:elements ld-network)))))
      (is (= 1 (count (filter #(= (:element-type %) "coil_negated") (:elements ld-network)))))))

  (testing "Converting timer instructions"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "TON" "T#5s" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)]

      (is (= 3 (count (:elements ld-network))))

      ;; Check timer element
      (let [timer-element (first (filter #(= (:element-type %) "timer_on") (:elements ld-network)))]
        (is (not (nil? timer-element)))
        (is (= "T#5s" (get-in timer-element [:properties :preset])))))))

(deftest ld-to-il-conversion-test
  (testing "Tracing simple LD network"
    (let [contact (converter/create-contact "X1" false {:x 1 :y 1})
          coil (converter/create-coil "Y1" false {:x 10 :y 1})
          ld-network (converter/->LDNetwork
                      "test-network"
                      [contact coil]
                      [{:source {:element (:id contact) :port "out"}
                        :target {:element (:id coil) :port "in"}}])
          il-instructions (converter/trace-ld-network ld-network)]

      (is (= 2 (count il-instructions)))
      (is (= "LD" (get-in il-instructions [0 :operator])))
      (is (= "X1" (get-in il-instructions [0 :operand])))
      (is (= "ST" (get-in il-instructions [1 :operator])))
      (is (= "Y1" (get-in il-instructions [1 :operand])))))

  (testing "Tracing LD network with AND function"
    (let [contact1 (converter/create-contact "X1" false {:x 1 :y 1})
          contact2 (converter/create-contact "X2" false {:x 1 :y 2})
          and-block (converter/create-function-block
                     "and"
                     {:x 5 :y 1.5}
                     [{:id "in1"} {:id "in2"}]
                     [{:id "out"}]
                     {})
          coil (converter/create-coil "Y1" false {:x 10 :y 1.5})
          ld-network (converter/->LDNetwork
                      "test-network"
                      [contact1 contact2 and-block coil]
                      [{:source {:element (:id contact1) :port "out"}
                        :target {:element (:id and-block) :port "in1"}}
                       {:source {:element (:id contact2) :port "out"}
                        :target {:element (:id and-block) :port "in2"}}
                       {:source {:element (:id and-block) :port "out"}
                        :target {:element (:id coil) :port "in"}}])
          il-instructions (converter/trace-ld-network ld-network)]

      ;; We might not be able to fully predict the exact order due to traversal,
      ;; but we can check that essential instructions are present
      (is (<= 3 (count il-instructions)))
      (is (some #(and (= (:operator %) "LD") (= (:operand %) "X1")) il-instructions))
      (is (some #(and (= (:operator %) "AND") (= (:operand %) "X2")) il-instructions))
      (is (some #(and (= (:operator %) "ST") (= (:operand %) "Y1")) il-instructions)))))

(deftest network-modification-test
  (testing "Adding and removing elements"
    (let [contact (converter/create-contact "X1" false {:x 1 :y 1})
          coil (converter/create-coil "Y1" false {:x 10 :y 1})
          network (converter/->LDNetwork "test-network" [contact] [])

          ;; Add a coil
          network-with-coil (converter/add-element network coil)

          ;; Remove the contact
          network-without-contact (converter/remove-element network-with-coil (:id contact))]

      (is (= 2 (count (:elements network-with-coil))))
      (is (= 1 (count (:elements network-without-contact))))
      (is (= "coil" (get-in network-without-contact [:elements 0 :element-type])))))

  (testing "Adding and removing connections"
    (let [contact (converter/create-contact "X1" false {:x 1 :y 1})
          coil (converter/create-coil "Y1" false {:x 10 :y 1})
          network (converter/->LDNetwork "test-network" [contact coil] [])

          ;; Add a connection
          network-with-conn (converter/add-connection
                             network
                             (:id contact) "out"
                             (:id coil) "in")

          ;; Remove the connection
          network-without-conn (converter/remove-connection
                                network-with-conn
                                (:id contact) "out"
                                (:id coil) "in")]

      (is (= 1 (count (:connections network-with-conn))))
      (is (= 0 (count (:connections network-without-conn))))))

  (testing "Updating element properties"
    (let [contact (converter/create-contact "X1" false {:x 1 :y 1})
          network (converter/->LDNetwork "test-network" [contact] [])

          ;; Update position
          network-with-new-pos (converter/update-element-position
                                network
                                (:id contact)
                                {:x 5 :y 5})

          ;; Update variable property
          network-with-new-var (converter/update-element-property
                                network
                                (:id contact)
                                :variable
                                "X2")]

      (is (= {:x 5 :y 5} (get-in network-with-new-pos [:elements 0 :position])))
      (is (= "X2" (get-in network-with-new-var [:elements 0 :properties :variable]))))))

(deftest json-serialization-test
  (testing "JSON serialization and deserialization"
    (let [contact (converter/create-contact "X1" false {:x 1 :y 1})
          coil (converter/create-coil "Y1" false {:x 10 :y 1})
          network (converter/->LDNetwork
                   "test-network"
                   [contact coil]
                   [{:source {:element (:id contact) :port "out"}
                     :target {:element (:id coil) :port "in"}}])

          ;; Convert to JSON and back
          json-str (converter/ld-to-json network)
          network-from-json (converter/json-to-ld json-str)]

      (is (string? json-str))
      (is (= 2 (count (:elements network-from-json))))
      (is (= 1 (count (:connections network-from-json))))
      (is (= "test-network" (:id network-from-json))))))