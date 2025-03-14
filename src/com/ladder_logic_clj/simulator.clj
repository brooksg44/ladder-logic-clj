(ns com.ladder-logic-clj.simulator
  "Provides functions for simulating LD networks"
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [com.ladder-logic-clj.converter :as converter]
            [com.ladder-logic-clj.specs :as specs]))

(declare evaluate-element)

;; ---- Variable and State Management ----

(defn create-variable
  "Create a variable with initial value"
  [name type initial-value]
  (let [variable {:name name
                  :type type
                  :value initial-value}]
    (if (s/valid? ::specs/variable-def variable)
      variable
      (throw (ex-info "Invalid variable definition"
                      (s/explain-data ::specs/variable-def variable))))))

(defn get-variable-value
  "Get the value of a variable from a variable collection"
  [variables var-name]
  (get-in (first (filter #(= (:name %) var-name) variables)) [:value]))

(defn set-variable-value
  "Set the value of a variable in a variable collection"
  [variables var-name new-value]
  (mapv (fn [var]
          (if (= (:name var) var-name)
            (assoc var :value new-value)
            var))
        variables))

;; ---- Evaluation Functions ----

(defn evaluate-contact
  "Evaluate a contact element"
  [element variables]
  (let [var-name (get-in element [:properties :variable])
        var-value (get-variable-value variables var-name)
        negated? (= (:element-type element) "contact_negated")]
    (if negated? (not var-value) var-value)))

(defn evaluate-and
  "Evaluate an AND function block"
  [element connections elements-map variables state-map]
  (let [in1-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                      (= (get-in % [:target :port]) "in1"))
                                connections))
        in2-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                      (= (get-in % [:target :port]) "in2"))
                                connections))
        in1-element (get elements-map (get-in in1-conn [:source :element]))
        in2-element (get elements-map (get-in in2-conn [:source :element]))
        in1-value (evaluate-element in1-element connections elements-map variables state-map)
        in2-value (evaluate-element in2-element connections elements-map variables state-map)]
    (and in1-value in2-value)))

(defn evaluate-or
  "Evaluate an OR function block"
  [element connections elements-map variables state-map]
  (let [in1-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                      (= (get-in % [:target :port]) "in1"))
                                connections))
        in2-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                      (= (get-in % [:target :port]) "in2"))
                                connections))
        in1-element (get elements-map (get-in in1-conn [:source :element]))
        in2-element (get elements-map (get-in in2-conn [:source :element]))
        in1-value (evaluate-element in1-element connections elements-map variables state-map)
        in2-value (evaluate-element in2-element connections elements-map variables state-map)]
    (or in1-value in2-value)))

(defn evaluate-not
  "Evaluate a NOT function block"
  [element connections elements-map variables state-map]
  (let [in-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                     (= (get-in % [:target :port]) "in"))
                               connections))
        in-element (get elements-map (get-in in-conn [:source :element]))
        in-value (evaluate-element in-element connections elements-map variables state-map)]
    (not in-value)))

(defn parse-time-preset
  "Parse a time preset (e.g., T#1s, T#500ms) into milliseconds"
  [preset]
  (let [preset-str (if (string? preset) preset "T#1s")
        time-value (read-string (second (re-find #"T#(\d+)" preset-str)))
        time-unit (last (re-find #"T#\d+([a-z]+)" preset-str))]
    (case time-unit
      "ms" time-value
      "s" (* time-value 1000)
      "m" (* time-value 60000)
      "h" (* time-value 3600000)
      1000))) ;; Default to 1s if parsing fails

(declare evaluate-element)

(defn evaluate-timer-on
  "Evaluate a TON timer function block"
  [element connections elements-map variables state-map]
  (let [in-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                     (= (get-in % [:target :port]) "in"))
                               connections))
        in-element (when in-conn (get elements-map (get-in in-conn [:source :element])))
        in-value (if in-element
                   (evaluate-element in-element connections elements-map variables state-map)
                   false)
        preset (get-in element [:properties :preset] "T#1s")
        timer-id (:id element)
        timer-state (get state-map timer-id {:running false :start-time 0 :elapsed 0})
        current-time (System/currentTimeMillis)
        preset-ms (parse-time-preset preset)

        ;; Update timer state
        new-timer-state (cond
                          ;; Start timer
                          (and in-value (not (:running timer-state)))
                          {:running true
                           :start-time current-time
                           :elapsed 0}

                          ;; Continue running timer
                          (and in-value (:running timer-state))
                          (let [elapsed (- current-time (:start-time timer-state))]
                            {:running true
                             :start-time (:start-time timer-state)
                             :elapsed elapsed})

                          ;; Reset timer
                          :else
                          {:running false
                           :start-time 0
                           :elapsed 0})

        ;; Timer output is true when elapsed time ≥ preset time
        timer-output (>= (:elapsed new-timer-state) preset-ms)]

    ;; Update timer state in state map
    (swap! state-map assoc timer-id new-timer-state)

    timer-output))

(defn evaluate-timer-off
  "Evaluate a TOF timer function block"
  [element connections elements-map variables state-map]
  (let [in-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                     (= (get-in % [:target :port]) "in"))
                               connections))
        in-element (when in-conn (get elements-map (get-in in-conn [:source :element])))
        in-value (if in-element
                   (evaluate-element in-element connections elements-map variables state-map)
                   false)
        preset (get-in element [:properties :preset] "T#1s")
        timer-id (:id element)
        timer-state (get state-map timer-id {:running false :start-time 0 :elapsed 0 :was-on false})
        current-time (System/currentTimeMillis)
        preset-ms (parse-time-preset preset)

        new-timer-state (cond
                           ;; Start timer on falling edge (input goes from true to false)
                          (and (not in-value) (:was-on timer-state))
                          {:running true
                           :start-time current-time
                           :elapsed 0
                           :was-on false}

                           ;; Continue running timer
                          (:running timer-state)
                          (let [elapsed (- current-time (:start-time timer-state))]
                            (if (>= elapsed preset-ms)
                              {:running false
                               :start-time 0
                               :elapsed preset-ms
                               :was-on in-value}
                              {:running true
                               :start-time (:start-time timer-state)
                               :elapsed elapsed
                               :was-on in-value}))

                           ;; Update was-on state when not running
                          :else
                          {:running false
                           :start-time 0
                           :elapsed 0
                           :was-on in-value})

        timer-output (or in-value (:running new-timer-state))]

    (swap! state-map assoc timer-id new-timer-state)
    timer-output))

(defn evaluate-counter-up
  "Evaluate a CTU counter function block"
  [element connections elements-map variables state-map]
  (let [cu-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                     (= (get-in % [:target :port]) "cu"))
                               connections))
        r-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                    (= (get-in % [:target :port]) "r"))
                              connections))
        cu-element (when cu-conn (get elements-map (get-in cu-conn [:source :element])))
        r-element (when r-conn (get elements-map (get-in r-conn [:source :element])))
        cu-value (if cu-element
                   (evaluate-element cu-element connections elements-map variables state-map)
                   false)
        r-value (if r-element
                  (evaluate-element r-element connections elements-map variables state-map)
                  false)
        preset (get-in element [:properties :preset] "10")
        preset-val (if (string? preset) (read-string preset) preset)
        counter-id (:id element)
        counter-state (get state-map counter-id {:count 0 :prev-cu false})

        ;; Update counter state
        new-counter-state (cond
                            ;; Reset counter
                            r-value
                            {:count 0 :prev-cu cu-value}

                            ;; Increment counter on rising edge
                            (and cu-value (not (:prev-cu counter-state)))
                            {:count (min (inc (:count counter-state)) preset-val)
                             :prev-cu true}

                            ;; No change
                            :else
                            {:count (:count counter-state)
                             :prev-cu cu-value})

        ;; Counter output is true when count ≥ preset
        counter-output (>= (:count new-counter-state) preset-val)]

    ;; Update counter state in state map
    (swap! state-map assoc counter-id new-counter-state)

    counter-output))

(defn evaluate-counter-down
  "Evaluate a CTD counter function block"
  [element connections elements-map variables state-map]
  (let [cd-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                     (= (get-in % [:target :port]) "cd"))
                               connections))
        ld-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                     (= (get-in % [:target :port]) "ld"))
                               connections))
        cd-element (when cd-conn (get elements-map (get-in cd-conn [:source :element])))
        ld-element (when ld-conn (get elements-map (get-in ld-conn [:source :element])))
        cd-value (if cd-element
                   (evaluate-element cd-element connections elements-map variables state-map)
                   false)
        ld-value (if ld-element
                   (evaluate-element ld-element connections elements-map variables state-map)
                   false)
        preset (get-in element [:properties :preset] "10")
        preset-val (if (string? preset) (read-string preset) preset)
        counter-id (:id element)
        counter-state (get state-map counter-id {:count preset-val :prev-cd false})

        ;; Update counter state
        new-counter-state (cond
                            ;; Load counter with preset
                            ld-value
                            {:count preset-val :prev-cd cd-value}

                            ;; Decrement counter on rising edge
                            (and cd-value (not (:prev-cd counter-state)))
                            {:count (max (dec (:count counter-state)) 0)
                             :prev-cd true}

                            ;; No change
                            :else
                            {:count (:count counter-state)
                             :prev-cd cd-value})

        ;; Counter output is true when count = 0
        counter-output (zero? (:count new-counter-state))]

    ;; Update counter state in state map
    (swap! state-map assoc counter-id new-counter-state)

    counter-output))

(defn evaluate-math
  "Evaluate a math function block (add, subtract, multiply, divide)"
  [element connections elements-map variables state-map type]
  (let [in1-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                      (= (get-in % [:target :port]) "in1"))
                                connections))
        in2-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                      (= (get-in % [:target :port]) "in2"))
                                connections))
        in1-element (when in1-conn (get elements-map (get-in in1-conn [:source :element])))
        in2-element (when in2-conn (get elements-map (get-in in2-conn [:source :element])))
        operand (get-in element [:properties :operand])
        operand-value (if (and operand (not (string? operand)))
                        operand
                        (if (string? operand)
                          (get-variable-value variables operand)
                          0))
        in1-value (if in1-element
                    (evaluate-element in1-element connections elements-map variables state-map)
                    0)
        in2-value (if in2-element
                    (evaluate-element in2-element connections elements-map variables state-map)
                    operand-value)]
    (try
      (case type
        "add" (+ in1-value in2-value)
        "subtract" (- in1-value in2-value)
        "multiply" (* in1-value in2-value)
        "divide" (if (zero? in2-value)
                   (throw (ArithmeticException. "Division by zero"))
                   (/ in1-value in2-value))
        0)
      (catch ArithmeticException _
        (println "Warning: Division by zero encountered in" (:id element))
        0))))

(defn evaluate-compare
  "Evaluate a comparison function block (GT, GE, EQ, NE, LE, LT)"
  [element connections elements-map variables state-map type]
  (let [in1-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                      (= (get-in % [:target :port]) "in1"))
                                connections))
        in2-conn (first (filter #(and (= (get-in % [:target :element]) (:id element))
                                      (= (get-in % [:target :port]) "in2"))
                                connections))
        in1-element (when in1-conn (get elements-map (get-in in1-conn [:source :element])))
        in2-element (when in2-conn (get elements-map (get-in in2-conn [:source :element])))
        operand (get-in element [:properties :operand])
        operand-value (if (and operand (not (string? operand)))
                        operand
                        (if (string? operand)
                          (get-variable-value variables operand)
                          0))
        in1-value (if in1-element
                    (evaluate-element in1-element connections elements-map variables state-map)
                    0)
        in2-value (if in2-element
                    (evaluate-element in2-element connections elements-map variables state-map)
                    operand-value)]

    (case type
      "greater_than" (> in1-value in2-value)
      "greater_equal" (>= in1-value in2-value)
      "equal" (= in1-value in2-value)
      "not_equal" (not= in1-value in2-value)
      "less_equal" (<= in1-value in2-value)
      "less_than" (< in1-value in2-value)
      false))) ;; Default case

(defn evaluate-element
  "Evaluate an LD element based on its type"
  [element connections elements-map variables state-map]
  (let [element-type (:element-type element)]
    (condp = element-type
      "contact" (evaluate-contact element variables)
      "contact_negated" (evaluate-contact element variables)
      "and" (evaluate-and element connections elements-map variables state-map)
      "or" (evaluate-or element connections elements-map variables state-map)
      "not" (evaluate-not element connections elements-map variables state-map)
      "timer_on" (evaluate-timer-on element connections elements-map variables state-map)
      "timer_off" (evaluate-timer-off element connections elements-map variables state-map)
      "counter_up" (evaluate-counter-up element connections elements-map variables state-map)
      "counter_down" (evaluate-counter-down element connections elements-map variables state-map)
      "add" (evaluate-math element connections elements-map variables state-map "add")
      "subtract" (evaluate-math element connections elements-map variables state-map "subtract")
      "multiply" (evaluate-math element connections elements-map variables state-map "multiply")
      "divide" (evaluate-math element connections elements-map variables state-map "divide")
      "greater_than" (evaluate-compare element connections elements-map variables state-map "greater_than")
      "greater_equal" (evaluate-compare element connections elements-map variables state-map "greater_equal")
      "equal" (evaluate-compare element connections elements-map variables state-map "equal")
      "not_equal" (evaluate-compare element connections elements-map variables state-map "not_equal")
      "less_equal" (evaluate-compare element connections elements-map variables state-map "less_equal")
      "less_than" (evaluate-compare element connections elements-map variables state-map "less_than")
      ;; Default case - unknown element type
      (do
        (println "Unknown element type for evaluation:" element-type)
        false))))

(defn simulate-ld-network
  "Simulate execution of an LD network for one cycle"
  [network variables state-map]
  (let [elements (:elements network)
        connections (:connections network)
        elements-map (reduce (fn [m elem] (assoc m (:id elem) elem)) {} elements)
        coil-elements (filter #(or (= (:element-type %) "coil") (= (:element-type %) "coil_negated")) elements)]

    ;; Evaluate all coil elements and update variables
    (doseq [coil coil-elements]
      (let [coil-var (get-in coil [:properties :variable])
            in-connections (filter #(and (= (get-in % [:target :element]) (:id coil))
                                         (= (get-in % [:target :port]) "in"))
                                   connections)]
        (when (and coil-var (not-empty in-connections))
          (let [in-element-id (get-in (first in-connections) [:source :element])
                in-element (get elements-map in-element-id)
                coil-value (evaluate-element in-element connections elements-map @variables state-map)
                negated? (= (:element-type coil) "coil_negated")
                final-value (if negated? (not coil-value) coil-value)]
            ;; Update variable value
            (swap! variables set-variable-value coil-var final-value)))))

    @variables))

 ;; ---- Element State Tracking ----

(defn update-element-states
  "Update the state map for all elements in the network based on evaluation results"
  [network elements-map variables state-map]
  (let [elements (:elements network)
        connections (:connections network)]

    ;; Update states for all elements
    (doseq [element elements]
      (let [element-id (:id element)
            element-type (:element-type element)
            element-result (try
                             (evaluate-element element connections elements-map @variables state-map)
                             (catch Exception e
                               (println "Error evaluating element" element-id ":" (.getMessage e))
                               false))]

        ;; Store element state in state-map
        (swap! state-map assoc element-id element-result)

        ;; For elements with outputs (like function blocks), store each output state
        (when (contains? #{"and" "or" "not" "timer_on" "timer_off" "timer_pulse"
                           "counter_up" "counter_down" "counter_updown"
                           "add" "subtract" "multiply" "divide"
                           "greater_than" "greater_equal" "equal" "not_equal"
                           "less_equal" "less_than"} element-type)
          (doseq [output (:outputs element)]
            (let [output-id (:id output)
                  output-connection-id (str element-id "_" output-id)]
              (swap! state-map assoc output-connection-id element-result)))))))

  ;; Update connection states
  (doseq [connection (:connections network)]
    (let [source-id (get-in connection [:source :element])
          source-port (get-in connection [:source :port])
          source-state (get @state-map source-id false)
          connection-id (str source-id "_" source-port)]
      (swap! state-map assoc connection-id source-state)))

  @state-map)

(defn run-simulation-cycle-with-visualization
  "Run a single simulation cycle, update states, and return a complete state map"
  [network variables state-map]
  (let [elements (:elements network)
        elements-map (reduce (fn [m elem] (assoc m (:id elem) elem)) {} elements)
        updated-vars (simulate-ld-network network variables state-map)
        updated-states (update-element-states network elements-map variables state-map)]

    ;; Return both updated variables and state map
    {:variables updated-vars
     :state-map @state-map}))

(defn initialize-variables
  "Initialize variables for simulation with default values"
  []
  (atom [(create-variable "X1" "BOOL" false)
         (create-variable "X2" "BOOL" false)
         (create-variable "X3" "BOOL" false)
         (create-variable "Y1" "BOOL" false)
         (create-variable "Y2" "BOOL" false)
         (create-variable "COUNT" "INT" 0)
         (create-variable "TIMER" "TIME" 0)]))

(defn initialize-state-map
  "Initialize state map for simulation"
  []
  (atom {}))

(defn run-simulation-cycle
  "Run a single simulation cycle and return updated variables"
  [network variables state-map]
  (simulate-ld-network network variables state-map))

(defn run-interactive-simulation
  "Run an interactive simulation of an LD network"
  [network & {:keys [with-visualization] :or {with-visualization false}}]
  (let [variables (initialize-variables)
        state-map (initialize-state-map)
        ;; Setup for continuous updates
        update-interval 200 ; 200ms between updates (5 fps)
        continuous-updates (atom with-visualization)
        last-update-time (atom (System/currentTimeMillis))]

    (println "Starting interactive simulation")
    (println "Type 'exit' to quit, 'set VAR VALUE' to set a variable,")
    (println "'continuous' to toggle continuous updates, or press Enter to run one cycle.")

    ;; Start visualization if requested
    (when with-visualization
      (require '[com.ladder-logic-clj.renderer :as renderer])
      ((resolve 'renderer/render-network) network variables state-map))

    ;; Start continuous update thread if visualization is enabled
    (when with-visualization
      (future
        (try
          (loop []
            (when @continuous-updates
              (let [current-time (System/currentTimeMillis)]
                (when (> (- current-time @last-update-time) update-interval)
                  ;; Run a simulation cycle
                  (simulate-ld-network network variables state-map)
                  (reset! last-update-time current-time))))
            (Thread/sleep 50) ; Small sleep to avoid CPU hogging
            (when with-visualization (recur)))
          (catch Exception e
            (println "Error in continuous simulation thread:" (.getMessage e))))))

    (loop [visualize with-visualization]
      (println "\nCurrent variables:")
      (doseq [var @variables]
        (println (str (:name var) " = " (:value var))))

      (print "> ")
      (flush)
      (let [input (read-line)]
        (cond
          (= input "exit")
          (do
            (reset! continuous-updates false) ; Stop the update thread
            (println "Simulation ended."))

          (= input "continuous")
          (do
            (swap! continuous-updates not)
            (println (if @continuous-updates
                       "Continuous updates enabled"
                       "Continuous updates disabled"))
            (when @continuous-updates
              (reset! last-update-time (System/currentTimeMillis)))
            (recur visualize))

          (= input "visual")
          (do
            (if visualize
              (println "Visualization disabled")
              (do
                (println "Visualization enabled")
                (require '[com.ladder-logic-clj.renderer :as renderer])
                ((resolve 'renderer/render-network) network variables state-map)))
            (recur (not visualize)))

          (str/starts-with? input "set")
          (let [parts (str/split input #"\s+")
                var-name (second parts)
                var-value (read-string (nth parts 2 "false"))]
            (swap! variables set-variable-value var-name var-value)
            (recur visualize))

          :else
          (do
            ;; Run a single simulation cycle
            (simulate-ld-network network variables state-map)
            (reset! last-update-time (System/currentTimeMillis))
            (recur visualize)))))))