(ns com.ladder-logic-clj.specstemp)
;; In specs.clj
;; Current problematic spec:
(s/def ::type #{"BOOL" "INT" "REAL" "TIME"})

;; This should be changed to:
(s/def ::variable-type #{"BOOL" "INT" "REAL" "TIME"}) ;; For variable types
(s/def ::element-type #{"contact" "contact_negated" "coil" "coil_negated"
                        "and" "or" "not" "timer_on" "timer_off" "timer_pulse"
                        "counter_up" "counter_down" "counter_updown"
                        "add" "subtract" "multiply" "divide"
                        "greater_than" "greater_equal" "equal" "not_equal"
                        "less_equal" "less_than"})

;; And then in your ::element spec, use ::element-type instead of ::type
(s/def ::element (s/keys :req-un [::element-type ::id ::position ::inputs ::outputs]
                         :opt-un [::properties]))