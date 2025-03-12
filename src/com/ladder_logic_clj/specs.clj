(ns com.ladder-logic-clj.specs
  "Spec definitions for Ladder Logic Clj types"
  (:require [clojure.spec.alpha :as s]
            [clojure.spec.gen.alpha :as gen]))

;; ---- Common Specs ----

;; Valid position specification
(s/def ::x number?)
(s/def ::y number?)
(s/def ::position (s/keys :req-un [::x ::y]))

;; Valid unique identifier
(s/def ::id string?)

;; Valid port identifier
(s/def ::port-id string?)
(s/def ::port (s/keys :req-un [::id]))

;; ---- IL Specs ----

;; Valid IL instruction operators
(s/def ::operator-type #{"LD" "LDN" "ST" "STN" "AND" "ANDN" "OR" "ORN" "XOR"
                         "NOT" "ADD" "SUB" "MUL" "DIV" "GT" "GE" "EQ" "NE"
                         "LE" "LT" "TON" "TOF" "TP" "CTU" "CTD" "CTUD" "R" "S"})
(s/def ::operator ::operator-type)
(s/def ::operand (s/nilable string?))
(s/def ::modifier (s/nilable string?))

;; IL Instruction record
(s/def ::instruction (s/keys :req-un [::operator]
                             :opt-un [::operand ::modifier]))

;; IL Program
(s/def ::instructions (s/coll-of ::instruction :kind vector?))
(s/def ::program (s/keys :req-un [::instructions]))

;; ---- LD Specs ----

;; Element types
(s/def ::element-type #{"contact" "contact_negated" "coil" "coil_negated"
                        "and" "or" "not" "timer_on" "timer_off" "timer_pulse"
                        "counter_up" "counter_down" "counter_updown"
                        "add" "subtract" "multiply" "divide"
                        "greater_than" "greater_equal" "equal" "not_equal"
                        "less_equal" "less_than"})

;; Element properties
(s/def ::variable string?)
(s/def ::preset string?)
(s/def ::properties (s/keys :opt-un [::variable ::preset]))

;; Inputs and Outputs
(s/def ::inputs (s/coll-of ::port :kind vector?))
(s/def ::outputs (s/coll-of ::port :kind vector?))

;; LD Element 
(s/def ::element (s/keys :req-un [::type ::id ::position ::inputs ::outputs]
                         :opt-un [::properties]))

;; Connection points
(s/def ::element-id ::id)
(s/def ::connection-point (s/keys :req-un [::element ::port]))
(s/def ::source ::connection-point)
(s/def ::target ::connection-point)

;; Connection
(s/def ::connection (s/keys :req-un [::source ::target]))

;; LD Network
(s/def ::elements (s/coll-of ::element :kind vector?))
(s/def ::connections (s/coll-of ::connection :kind vector?))
(s/def ::network (s/keys :req-un [::id ::elements ::connections]))

;; ---- Simulation State Specs ----

(s/def ::name string?)
(s/def ::type #{"BOOL" "INT" "REAL" "TIME"})
(s/def ::value (s/or :bool boolean?
                     :num number?
                     :str string?))

;; Variable
(s/def ::variable-def (s/keys :req-un [::name ::type ::value]))

;; Timer state
(s/def ::running boolean?)
(s/def ::start-time number?)
(s/def ::elapsed number?)
(s/def ::was-on boolean?)
(s/def ::timer-state (s/keys :req-un [::running ::start-time ::elapsed]
                             :opt-un [::was-on]))

;; Counter state
(s/def ::count number?)
(s/def ::prev-cu boolean?)
(s/def ::prev-cd boolean?)
(s/def ::counter-state (s/keys :req-un [::count]
                               :opt-un [::prev-cu ::prev-cd]))

;; Function to validate and conform data against specs
(defn validate
  "Validate data against a spec. Returns [true conformed-data] if valid,
   [false error-data] if invalid."
  [spec data]
  (if (s/valid? spec data)
    [true (s/conform spec data)]
    [false (s/explain-data spec data)]))

;; Functions for generating examples
(defn generate-example
  "Generate an example that conforms to the given spec"
  [spec]
  (gen/generate (s/gen spec)))

;; Helper functions for creating spec-conforming objects
(defn create-instruction
  "Create a spec-conforming IL instruction"
  [operator operand modifier]
  (let [instr {:operator operator
               :operand operand
               :modifier modifier}
        [valid? result] (validate ::instruction instr)]
    (if valid?
      instr
      (throw (ex-info "Invalid IL instruction" result)))))

(defn create-element
  "Create a spec-conforming LD element"
  [type id position inputs outputs properties]
  (let [elem {:type type
              :id id
              :position position
              :inputs inputs
              :outputs outputs
              :properties properties}
        [valid? result] (validate ::element elem)]
    (if valid?
      elem
      (throw (ex-info "Invalid LD element" result)))))

(defn create-connection
  "Create a spec-conforming LD connection"
  [source-element source-port target-element target-port]
  (let [conn {:source {:element source-element
                       :port source-port}
              :target {:element target-element
                       :port target-port}}
        [valid? result] (validate ::connection conn)]
    (if valid?
      conn
      (throw (ex-info "Invalid LD connection" result)))))

(defn create-network
  "Create a spec-conforming LD network"
  [id elements connections]
  (let [net {:id id
             :elements elements
             :connections connections}
        [valid? result] (validate ::network net)]
    (if valid?
      net
      (throw (ex-info "Invalid LD network" result)))))