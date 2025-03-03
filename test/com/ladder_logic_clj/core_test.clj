(ns com.ladder-logic-clj.core-test
  (:require [clojure.test :refer :all]
            [com.ladder-logic-clj.parser :as parser]
            [com.ladder-logic-clj.converter :as converter]
            [com.ladder-logic-clj.simulator :as simulator]))

(deftest parse-il-instruction-test
  (testing "Parsing simple IL instructions"
    (is (= (parser/parse-il-instruction "LD X1")
           (parser/->ILInstruction "LD" "X1" nil)))

    (is (= (parser/parse-il-instruction "AND X2")
           (parser/->ILInstruction "AND" "X2" nil)))

    (is (= (parser/parse-il-instruction "ST Y1")
           (parser/->ILInstruction "ST" "Y1" nil)))))

(deftest parse-il-program-test
  (testing "Parsing complete IL program"
    (let [program "LD X1\nAND X2\nST Y1"
          instructions (parser/parse-il-program program)]
      (is (= 3 (count instructions)))
      (is (= "LD" (get-in instructions [0 :operator])))
      (is (= "X1" (get-in instructions [0 :operand])))
      (is (= "AND" (get-in instructions [1 :operator])))
      (is (= "X2" (get-in instructions [1 :operand])))
      (is (= "ST" (get-in instructions [2 :operator])))
      (is (= "Y1" (get-in instructions [2 :operand])))))

  (testing "Parsing IL program with comments and blank lines"
    (let [program "; Comment\n\nLD X1\n; Another comment\nAND X2\n\nST Y1"
          instructions (parser/parse-il-program program)]
      (is (= 3 (count instructions)))
      (is (= "LD" (get-in instructions [0 :operator])))
      (is (= "X1" (get-in instructions [0 :operand])))
      (is (= "AND" (get-in instructions [1 :operator])))
      (is (= "X2" (get-in instructions [1 :operand])))
      (is (= "ST" (get-in instructions [2 :operator])))
      (is (= "Y1" (get-in instructions [2 :operand]))))))

(deftest il-to-ld-conversion-test
  (testing "Converting basic IL to LD"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "AND" "X2" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)]

      ;; Check elements
      (is (= 4 (count (:elements ld-network))))

      ;; Check element types
      (is (= "contact" (get-in ld-network [:elements 0 :type])))
      (is (= "contact" (get-in ld-network [:elements 1 :type])))
      (is (= "and" (get-in ld-network [:elements 2 :type])))
      (is (= "coil" (get-in ld-network [:elements 3 :type])))

      ;; Check variables
      (is (= "X1" (get-in ld-network [:elements 0 :properties :variable])))
      (is (= "X2" (get-in ld-network [:elements 1 :properties :variable])))
      (is (= "Y1" (get-in ld-network [:elements 3 :properties :variable])))

      ;; Check connections
      (is (= 3 (count (:connections ld-network)))))))

(deftest ld-to-il-conversion-test
  (testing "Converting LD back to IL"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "AND" "X2" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)
          new-instructions (converter/trace-ld-network ld-network)]

      ;; Check that conversion back produces valid IL
      (is (seq new-instructions))
      (is (= "LD" (get-in new-instructions [0 :operator])))
      (is (= "AND" (some #(when (= (:operator %) "AND") (:operator %)) new-instructions)))
      (is (= "ST" (some #(when (= (:operator %) "ST") (:operator %)) new-instructions))))))

(deftest simulation-test
  (testing "Simulating a basic LD network"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "AND" "X2" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)
          variables (atom [(simulator/create-variable "X1" "BOOL" true)
                           (simulator/create-variable "X2" "BOOL" true)
                           (simulator/create-variable "Y1" "BOOL" false)])
          state-map (atom {})
          updated-vars (simulator/run-simulation-cycle ld-network variables state-map)]

      ;; Y1 should be true because X1 AND X2 are both true
      (is (= true (simulator/get-variable-value updated-vars "Y1"))))))

(deftest timer-test
  (testing "Simulating a timer"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "TON" "T#100ms" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)
          variables (atom [(simulator/create-variable "X1" "BOOL" true)
                           (simulator/create-variable "Y1" "BOOL" false)])
          state-map (atom {})]

      ;; First cycle - timer starts but not elapsed yet
      (let [updated-vars (simulator/run-simulation-cycle ld-network variables state-map)]
        (is (= false (simulator/get-variable-value updated-vars "Y1"))))

      ;; Wait for timer to elapse
      (Thread/sleep 110)

      ;; Second cycle - timer should have elapsed
      (let [updated-vars (simulator/run-simulation-cycle ld-network variables state-map)]
        (is (= true (simulator/get-variable-value updated-vars "Y1")))))))

(deftest counter-test
  (testing "Simulating a counter"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "CTU" "3" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          ld-network (converter/il-to-ld-network instructions)
          variables (atom [(simulator/create-variable "X1" "BOOL" false)
                           (simulator/create-variable "Y1" "BOOL" false)])
          state-map (atom {})]

      ;; First pulse - counter at 1
      (swap! variables simulator/set-variable-value "X1" true)
      (simulator/run-simulation-cycle ld-network variables state-map)
      (swap! variables simulator/set-variable-value "X1" false)
      (let [result1 (simulator/run-simulation-cycle ld-network variables state-map)]
        (is (= false (simulator/get-variable-value result1 "Y1"))))

      ;; Second pulse - counter at 2
      (swap! variables simulator/set-variable-value "X1" true)
      (simulator/run-simulation-cycle ld-network variables state-map)
      (swap! variables simulator/set-variable-value "X1" false)
      (let [result2 (simulator/run-simulation-cycle ld-network variables state-map)]
        (is (= false (simulator/get-variable-value result2 "Y1"))))

      ;; Third pulse - counter reaches 3, output should become true
      (swap! variables simulator/set-variable-value "X1" true)
      (simulator/run-simulation-cycle ld-network variables state-map)
      (swap! variables simulator/set-variable-value "X1" false)
      (let [result3 (simulator/run-simulation-cycle ld-network variables state-map)]
        (is (= true (simulator/get-variable-value result3 "Y1")))))))