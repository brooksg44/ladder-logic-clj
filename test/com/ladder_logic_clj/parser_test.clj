(ns com.ladder-logic-clj.parser-test
  (:require [clojure.test :refer :all]
            [com.ladder-logic-clj.parser :as parser]
            [clojure.data.json :as json]))

(deftest instruction-parsing-test
  (testing "Basic instruction parsing"
    (is (= (parser/->ILInstruction "LD" "X1" nil)
           (parser/parse-il-instruction "LD X1")))

    (is (= (parser/->ILInstruction "ST" "Y1" nil)
           (parser/parse-il-instruction "ST Y1")))

    (is (= (parser/->ILInstruction "AND" "X2" nil)
           (parser/parse-il-instruction "AND X2")))

    (is (= (parser/->ILInstruction "OR" "X3" nil)
           (parser/parse-il-instruction "OR X3"))))

  (testing "Instructions with modifiers"
    (is (= (parser/->ILInstruction "LD" nil "N")
           (parser/parse-il-instruction "LD(N)")))

    (is (= (parser/->ILInstruction "ST" "Y1" "N")
           (parser/parse-il-instruction "ST(N) Y1")))

    (is (= (parser/->ILInstruction "AND" "X2" "N")
           (parser/parse-il-instruction "AND(N) X2"))))

  (testing "Instructions without operands"
    (is (= (parser/->ILInstruction "NOT" nil nil)
           (parser/parse-il-instruction "NOT")))

    (is (= (parser/->ILInstruction "RET" nil nil)
           (parser/parse-il-instruction "RET"))))

  (testing "Handling extra whitespace"
    (is (= (parser/->ILInstruction "LD" "X1" nil)
           (parser/parse-il-instruction "  LD   X1  ")))

    (is (= (parser/->ILInstruction "ADD" "42" nil)
           (parser/parse-il-instruction "ADD    42")))))

(deftest program-parsing-test
  (testing "Empty program"
    (is (empty? (parser/parse-il-program ""))))

  (testing "Simple program"
    (let [program "LD X1\nAND X2\nST Y1"
          instructions (parser/parse-il-program program)]

      (is (= 3 (count instructions)))
      (is (= "LD" (:operator (first instructions))))
      (is (= "X1" (:operand (first instructions))))
      (is (= "AND" (:operator (second instructions))))
      (is (= "X2" (:operand (second instructions))))
      (is (= "ST" (:operator (last instructions))))
      (is (= "Y1" (:operand (last instructions))))))

  (testing "Program with comments and blank lines"
    (let [program "; This is a comment\n\nLD X1 ; Load X1\n; Another comment\nAND X2\n\nST Y1"
          instructions (parser/parse-il-program program)]

      (is (= 3 (count instructions)))
      (is (= "LD" (:operator (first instructions))))
      (is (= "X1" (:operand (first instructions))))
      (is (= "AND" (:operator (second instructions))))
      (is (= "X2" (:operand (second instructions))))
      (is (= "ST" (:operator (last instructions))))
      (is (= "Y1" (:operand (last instructions))))))

  (testing "Program with advanced instructions"
    (let [program "LD X1\nTON T#5s\nST Y1\nLD X2\nCTU 10\nST Y2\nLD X3\nADD 5\nMUL 2\nST Result"
          instructions (parser/parse-il-program program)]

      (is (= 10 (count instructions)))
      (is (= "TON" (:operator (nth instructions 1))))
      (is (= "T#5s" (:operand (nth instructions 1))))
      (is (= "CTU" (:operator (nth instructions 4))))
      (is (= "10" (:operand (nth instructions 4))))
      (is (= "ADD" (:operator (nth instructions 7))))
      (is (= "5" (:operand (nth instructions 7))))
      (is (= "MUL" (:operator (nth instructions 8))))
      (is (= "2" (:operand (nth instructions 8)))))))

(deftest format-instruction-test
  (testing "Formatting basic instructions"
    (is (= "LD X1"
           (parser/format-il-instruction (parser/->ILInstruction "LD" "X1" nil))))

    (is (= "ST Y1"
           (parser/format-il-instruction (parser/->ILInstruction "ST" "Y1" nil))))

    (is (= "AND X2"
           (parser/format-il-instruction (parser/->ILInstruction "AND" "X2" nil)))))

  (testing "Formatting instructions with modifiers"
    (is (= "LD(N)"
           (parser/format-il-instruction (parser/->ILInstruction "LD" nil "N"))))

    (is (= "ST(N) Y1"
           (parser/format-il-instruction (parser/->ILInstruction "ST" "Y1" "N")))))

  (testing "Formatting instructions without operands"
    (is (= "NOT"
           (parser/format-il-instruction (parser/->ILInstruction "NOT" nil nil))))

    (is (= "RET"
           (parser/format-il-instruction (parser/->ILInstruction "RET" nil nil))))))

(deftest program-formatting-test
  (testing "Formatting a complete program"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "AND" "X2" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          formatted (parser/format-il-program instructions)]

      (is (= "LD X1\nAND X2\nST Y1" formatted)))))

(deftest json-serialization-test
  (testing "JSON serialization"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "AND" "X2" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          json-str (parser/il-to-json instructions)
          parsed (json/read-str json-str :key-fn keyword)]

      (is (string? json-str))
      (is (= 3 (count (get-in parsed [:program :instructions]))))
      (is (= "LD" (get-in parsed [:program :instructions 0 :operator])))
      (is (= "X1" (get-in parsed [:program :instructions 0 :operand])))
      (is (= "AND" (get-in parsed [:program :instructions 1 :operator])))
      (is (= "X2" (get-in parsed [:program :instructions 1 :operand])))
      (is (= "ST" (get-in parsed [:program :instructions 2 :operator])))
      (is (= "Y1" (get-in parsed [:program :instructions 2 :operand])))))

  (testing "JSON deserialization"
    (let [json-str "{\"program\":{\"instructions\":[{\"operator\":\"LD\",\"operand\":\"X1\",\"modifier\":null},{\"operator\":\"AND\",\"operand\":\"X2\",\"modifier\":null},{\"operator\":\"ST\",\"operand\":\"Y1\",\"modifier\":null}]}}"
          instructions (parser/json-to-il json-str)]

      (is (= 3 (count instructions)))
      (is (= "LD" (:operator (first instructions))))
      (is (= "X1" (:operand (first instructions))))
      (is (= "AND" (:operator (second instructions))))
      (is (= "X2" (:operand (second instructions))))
      (is (= "ST" (:operator (last instructions))))
      (is (= "Y1" (:operand (last instructions)))))))

(deftest file-operations-test
  (testing "Save and load IL instructions to/from file"
    (let [instructions [(parser/->ILInstruction "LD" "X1" nil)
                        (parser/->ILInstruction "AND" "X2" nil)
                        (parser/->ILInstruction "ST" "Y1" nil)]
          temp-file (str (System/getProperty "java.io.tmpdir") "/test-il.json")]

      ;; Save to file
      (parser/save-il-to-file instructions temp-file)

      ;; Load from file
      (let [loaded-instructions (parser/load-il-from-file temp-file)]
        (is (= 3 (count loaded-instructions)))
        (is (= "LD" (:operator (first loaded-instructions))))
        (is (= "X1" (:operand (first loaded-instructions))))
        (is (= "AND" (:operator (second loaded-instructions))))
        (is (= "X2" (:operand (second loaded-instructions))))
        (is (= "ST" (:operator (last loaded-instructions))))
        (is (= "Y1" (:operand (last loaded-instructions))))))))