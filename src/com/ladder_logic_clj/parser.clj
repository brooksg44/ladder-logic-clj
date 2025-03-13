(ns com.ladder-logic-clj.parser
  "Provides functions for parsing IEC 61131-3 IL programs"
  (:require [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [com.ladder-logic-clj.specs :as specs]))

;; Data structures
(defrecord ILInstruction [operator operand modifier])

(defn validate-instruction
  "Validate an IL instruction against specs"
  [instruction]
  (when-not (s/valid? ::specs/instruction instruction)
    (println "Warning: Invalid IL instruction:" (pr-str instruction))
    (s/explain ::specs/instruction instruction))
  instruction)

(defn parse-il-instruction
  "Parse a single IL instruction string into an ILInstruction record"
  [instruction-str]
  (let [trimmed (str/trim instruction-str)
        parts (str/split trimmed #"\s+")
        operator-part (first parts)
        ;; Improved modifier parsing
        [operator modifier] (if (and operator-part (str/includes? operator-part "("))
                              (let [[_ op mod] (re-find #"(\w+)\((.*?)\)" operator-part)]
                                [op (when (not-empty mod) mod)])
                              [operator-part nil])
        operand (when (> (count parts) 1)
                  (str/join " " (rest parts)))] ;; Join remaining parts for operands with spaces
    (validate-instruction (->ILInstruction operator operand modifier))))

(defn parse-il-program
  "Parse a complete IL program into a sequence of ILInstruction records.
   Handles comments and blank lines."
  [il-program]
  (let [lines (filter #(not (or (str/blank? %)
                                (str/starts-with? (str/trim %) ";")))
                      (str/split-lines il-program))]
    (let [instructions (mapv parse-il-instruction lines)]
      (when-not (s/valid? ::specs/instructions instructions)
        (println "Warning: Invalid IL program structure"))
      instructions)))

(defn il-to-json
  "Convert IL instructions to JSON format"
  [il-instructions]
  (json/write-str
   {:program
    {:instructions
     (mapv (fn [instr]
             {:operator (:operator instr)
              :operand (:operand instr)
              :modifier (:modifier instr)})
           il-instructions)}}))

(defn json-to-il
  "Convert JSON format back to IL instructions"
  [json-data]
  (let [parsed (json/read-str json-data :key-fn keyword)
        instructions (get-in parsed [:program :instructions])]
    (mapv (fn [instr]
            (validate-instruction (->ILInstruction (:operator instr) (:operand instr) (:modifier instr))))
          instructions)))

(defn save-il-to-file
  "Save IL instructions to a JSON file"
  [il-instructions filename]
  (spit filename (il-to-json il-instructions)))

(defn load-il-from-file
  "Load IL instructions from a JSON file"
  [filename]
  (json-to-il (slurp filename)))

;; Helper function to pretty-print IL instructions
(defn format-il-instruction
  "Format an IL instruction as a string"
  [instruction]
  (let [{:keys [operator operand modifier]} instruction
        op-str (if modifier
                 (str operator "(" modifier ")")
                 operator)
        op-str (str op-str (when operand (str " " operand)))]
    op-str))

(defn format-il-program
  "Format IL instructions as a complete program"
  [instructions]
  (str/join "\n" (map format-il-instruction instructions)))