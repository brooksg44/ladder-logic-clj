(ns com.ladder-logic-clj.converter
  "Provides functions for converting between IL and LD formats"
  (:require [clojure.data.json :as json]
            [clojure.string :as str]
            [clojure.spec.alpha :as s]
            [com.ladder-logic-clj.specs :as specs]
            [com.ladder-logic-clj.parser :as parser]))

;; LD element structure
(defrecord LDElement [element-type id position inputs outputs properties]
  Object
  (toString [this]
    (str "#LDElement{:element-type \"" element-type "\", :id \"" id "\", :position " position "}")))

;; LD Network structure
(defrecord LDNetwork [id elements connections]
  Object
  (toString [this]
    (str "#LDNetwork{:id \"" id "\", :elements-count " (count elements)
         ", :connections-count " (count connections) "}")))

;; Function to validate LDElement against specs
(defn validate-element [element]
  (if (s/valid? ::specs/element element)
    element
    (throw (ex-info "Invalid LD element"
                    (s/explain-data ::specs/element element)))))

;; Function to validate LDNetwork against specs
(defn validate-network [network]
  (if (s/valid? ::specs/network network)
    network
    (throw (ex-info "Invalid LD network"
                    (s/explain-data ::specs/network network)))))

;; ---- Helper Functions ----

(defn generate-unique-id
  "Generate a unique ID for LD elements"
  []
  (str (java.util.UUID/randomUUID)))

(defn create-contact
  "Create a contact element for LD"
  [operand negated? position]
  (validate-element
   (->LDElement
    (if negated? "contact_negated" "contact")
    (generate-unique-id)
    position
    [{:id "in"}]
    [{:id "out"}]
    {:variable operand})))

(defn create-coil
  "Create a coil element for LD"
  [operand negated? position]
  (validate-element
   (->LDElement
    (if negated? "coil_negated" "coil")
    (generate-unique-id)
    position
    [{:id "in"}]
    [{:id "out"}]
    {:variable operand})))

(defn create-function-block
  "Create a function block element for LD"
  [element-type position inputs outputs properties]
  (validate-element
   (->LDElement
    element-type
    (generate-unique-id)
    position
    inputs
    outputs
    properties)))

;; ---- IL to LD Conversion ----

(defn il-to-ld-network
  "Convert a sequence of IL instructions to an LD network"
  [il-instructions]
  (loop [instructions il-instructions
         elements []
         connections []
         current-node nil
         position {:x 1 :y 1}]
    (if (empty? instructions)
      (validate-network (->LDNetwork (generate-unique-id) elements connections))
      (let [instr (first instructions)
            operator (:operator instr)
            operand (:operand instr)
            modifier (:modifier instr)]
        (condp = operator
          "LD" (let [new-element (create-contact operand false position)]
                 (recur (rest instructions)
                        (conj elements new-element)
                        connections
                        new-element
                        (update position :x + 2)))

          "LDN" (let [new-element (create-contact operand true position)]
                  (recur (rest instructions)
                         (conj elements new-element)
                         connections
                         new-element
                         (update position :x + 2)))

          "ST" (let [new-element (create-coil operand false position)]
                 (recur (rest instructions)
                        (conj elements new-element)
                        (if current-node
                          (conj connections {:source {:element (:id current-node) :port "out"}
                                             :target {:element (:id new-element) :port "in"}})
                          connections)
                        nil
                        (update position :x + 2)))

          "STN" (let [new-element (create-coil operand true position)]
                  (recur (rest instructions)
                         (conj elements new-element)
                         (if current-node
                           (conj connections {:source {:element (:id current-node) :port "out"}
                                              :target {:element (:id new-element) :port "in"}})
                           connections)
                         nil
                         (update position :x + 2)))

          "AND" (let [new-element (create-contact operand false position)
                      and-block (create-function-block "and"
                                                       (update position :x + 2)
                                                       [{:id "in1"} {:id "in2"}]
                                                       [{:id "out"}]
                                                       {})]
                  (recur (rest instructions)
                         (conj elements new-element and-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id and-block) :port "in1"}}
                                 {:source {:element (:id new-element) :port "out"}
                                  :target {:element (:id and-block) :port "in2"}})
                           connections)
                         and-block
                         (update position :x + 4)))

          "ANDN" (let [new-element (create-contact operand true position)
                       and-block (create-function-block "and"
                                                        (update position :x + 2)
                                                        [{:id "in1"} {:id "in2"}]
                                                        [{:id "out"}]
                                                        {})]
                   (recur (rest instructions)
                          (conj elements new-element and-block)
                          (if current-node
                            (conj connections
                                  {:source {:element (:id current-node) :port "out"}
                                   :target {:element (:id and-block) :port "in1"}}
                                  {:source {:element (:id new-element) :port "out"}
                                   :target {:element (:id and-block) :port "in2"}})
                            connections)
                          and-block
                          (update position :x + 4)))

          "OR" (let [new-element (create-contact operand false position)
                     or-block (create-function-block "or"
                                                     (update position :x + 2)
                                                     [{:id "in1"} {:id "in2"}]
                                                     [{:id "out"}]
                                                     {})]
                 (recur (rest instructions)
                        (conj elements new-element or-block)
                        (if current-node
                          (conj connections
                                {:source {:element (:id current-node) :port "out"}
                                 :target {:element (:id or-block) :port "in1"}}
                                {:source {:element (:id new-element) :port "out"}
                                 :target {:element (:id or-block) :port "in2"}})
                          connections)
                        or-block
                        (update position :x + 4)))

          "ORN" (let [new-element (create-contact operand true position)
                      or-block (create-function-block "or"
                                                      (update position :x + 2)
                                                      [{:id "in1"} {:id "in2"}]
                                                      [{:id "out"}]
                                                      {})]
                  (recur (rest instructions)
                         (conj elements new-element or-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id or-block) :port "in1"}}
                                 {:source {:element (:id new-element) :port "out"}
                                  :target {:element (:id or-block) :port "in2"}})
                           connections)
                         or-block
                         (update position :x + 4)))

          "NOT" (let [not-block (create-function-block "not"
                                                       position
                                                       [{:id "in"}]
                                                       [{:id "out"}]
                                                       {})]
                  (recur (rest instructions)
                         (conj elements not-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id not-block) :port "in"}})
                           connections)
                         not-block
                         (update position :x + 2)))

          ;; Timer functions
          "TON" (let [timer-block (create-function-block "timer_on"
                                                         position
                                                         [{:id "in"} {:id "preset"}]
                                                         [{:id "q"} {:id "et"}]
                                                         {:preset (or operand "T#1s")})]
                  (recur (rest instructions)
                         (conj elements timer-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id timer-block) :port "in"}})
                           connections)
                         timer-block
                         (update position :x + 3)))

          "TOF" (let [timer-block (create-function-block "timer_off"
                                                         position
                                                         [{:id "in"} {:id "preset"}]
                                                         [{:id "q"} {:id "et"}]
                                                         {:preset (or operand "T#1s")})]
                  (recur (rest instructions)
                         (conj elements timer-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id timer-block) :port "in"}})
                           connections)
                         timer-block
                         (update position :x + 3)))

          ;; Counter functions
          "CTU" (let [counter-block (create-function-block "counter_up"
                                                           position
                                                           [{:id "cu"} {:id "r"} {:id "pv"}]
                                                           [{:id "q"} {:id "cv"}]
                                                           {:preset (or operand "10")})]
                  (recur (rest instructions)
                         (conj elements counter-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id counter-block) :port "cu"}})
                           connections)
                         counter-block
                         (update position :x + 3)))

          "CTD" (let [counter-block (create-function-block "counter_down"
                                                           position
                                                           [{:id "cd"} {:id "ld"} {:id "pv"}]
                                                           [{:id "q"} {:id "cv"}]
                                                           {:preset (or operand "10")})]
                  (recur (rest instructions)
                         (conj elements counter-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id counter-block) :port "cd"}})
                           connections)
                         counter-block
                         (update position :x + 3)))

          ;; Math functions
          "ADD" (let [add-block (create-function-block "add"
                                                       position
                                                       [{:id "in1"} {:id "in2"}]
                                                       [{:id "out"}]
                                                       {:operand operand})]
                  (recur (rest instructions)
                         (conj elements add-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id add-block) :port "in1"}})
                           connections)
                         add-block
                         (update position :x + 3)))

          "SUB" (let [sub-block (create-function-block "subtract"
                                                       position
                                                       [{:id "in1"} {:id "in2"}]
                                                       [{:id "out"}]
                                                       {:operand operand})]
                  (recur (rest instructions)
                         (conj elements sub-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id sub-block) :port "in1"}})
                           connections)
                         sub-block
                         (update position :x + 3)))

          "MUL" (let [mul-block (create-function-block "multiply"
                                                       position
                                                       [{:id "in1"} {:id "in2"}]
                                                       [{:id "out"}]
                                                       {:operand operand})]
                  (recur (rest instructions)
                         (conj elements mul-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id mul-block) :port "in1"}})
                           connections)
                         mul-block
                         (update position :x + 3)))

          "DIV" (let [div-block (create-function-block "divide"
                                                       position
                                                       [{:id "in1"} {:id "in2"}]
                                                       [{:id "out"}]
                                                       {:operand operand})]
                  (recur (rest instructions)
                         (conj elements div-block)
                         (if current-node
                           (conj connections
                                 {:source {:element (:id current-node) :port "out"}
                                  :target {:element (:id div-block) :port "in1"}})
                           connections)
                         div-block
                         (update position :x + 3)))

          ;; Comparison functions
          "GT" (let [gt-block (create-function-block "greater_than"
                                                     position
                                                     [{:id "in1"} {:id "in2"}]
                                                     [{:id "out"}]
                                                     {:operand operand})]
                 (recur (rest instructions)
                        (conj elements gt-block)
                        (if current-node
                          (conj connections
                                {:source {:element (:id current-node) :port "out"}
                                 :target {:element (:id gt-block) :port "in1"}})
                          connections)
                        gt-block
                        (update position :x + 3)))

          "GE" (let [ge-block (create-function-block "greater_equal"
                                                     position
                                                     [{:id "in1"} {:id "in2"}]
                                                     [{:id "out"}]
                                                     {:operand operand})]
                 (recur (rest instructions)
                        (conj elements ge-block)
                        (if current-node
                          (conj connections
                                {:source {:element (:id current-node) :port "out"}
                                 :target {:element (:id ge-block) :port "in1"}})
                          connections)
                        ge-block
                        (update position :x + 3)))

          "EQ" (let [eq-block (create-function-block "equal"
                                                     position
                                                     [{:id "in1"} {:id "in2"}]
                                                     [{:id "out"}]
                                                     {:operand operand})]
                 (recur (rest instructions)
                        (conj elements eq-block)
                        (if current-node
                          (conj connections
                                {:source {:element (:id current-node) :port "out"}
                                 :target {:element (:id eq-block) :port "in1"}})
                          connections)
                        eq-block
                        (update position :x + 3)))

          "NE" (let [ne-block (create-function-block "not_equal"
                                                     position
                                                     [{:id "in1"} {:id "in2"}]
                                                     [{:id "out"}]
                                                     {:operand operand})]
                 (recur (rest instructions)
                        (conj elements ne-block)
                        (if current-node
                          (conj connections
                                {:source {:element (:id current-node) :port "out"}
                                 :target {:element (:id ne-block) :port "in1"}})
                          connections)
                        ne-block
                        (update position :x + 3)))

          "LE" (let [le-block (create-function-block "less_equal"
                                                     position
                                                     [{:id "in1"} {:id "in2"}]
                                                     [{:id "out"}]
                                                     {:operand operand})]
                 (recur (rest instructions)
                        (conj elements le-block)
                        (if current-node
                          (conj connections
                                {:source {:element (:id current-node) :port "out"}
                                 :target {:element (:id le-block) :port "in1"}})
                          connections)
                        le-block
                        (update position :x + 3)))

          "LT" (let [lt-block (create-function-block "less_than"
                                                     position
                                                     [{:id "in1"} {:id "in2"}]
                                                     [{:id "out"}]
                                                     {:operand operand})]
                 (recur (rest instructions)
                        (conj elements lt-block)
                        (if current-node
                          (conj connections
                                {:source {:element (:id current-node) :port "out"}
                                 :target {:element (:id lt-block) :port "in1"}})
                          connections)
                        lt-block
                        (update position :x + 3)))

          ;; Default case - unknown instruction
          (do
            (println "Unknown IL instruction:" operator)
            (recur (rest instructions)
                   elements
                   connections
                   current-node
                   position)))))))

;; ---- LD to IL Conversion ----

(defn get-element-by-id
  "Get an element by its ID from a list of elements"
  [elements id]
  (first (filter #(= (:id %) id) elements)))

(defn get-connected-elements
  "Get elements connected to the given element's output"
  [element-id output-port connections]
  (filter #(and (= (get-in % [:source :element]) element-id)
                (= (get-in % [:source :port]) output-port))
          connections))

(defn get-input-elements
  "Get elements connected to the given element's input"
  [element-id input-port connections]
  (filter #(and (= (get-in % [:target :element]) element-id)
                (= (get-in % [:target :port]) input-port))
          connections))

(defn trace-ld-network
  "Trace through an LD network to generate IL instructions"
  [network]
  (let [elements (:elements network)
        connections (:connections network)
        start-elements (filter #(empty? (get-input-elements (:id %) "in" connections)) elements)]
    (loop [current-elements start-elements
           visited #{}
           instructions []]
      (if (empty? current-elements)
        instructions
        (let [element (first current-elements)
              element-id (:id element)
              element-type (:element-type element)]
          (if (contains? visited element-id)
            (recur (rest current-elements) visited instructions)
            (let [next-connections (get-connected-elements element-id "out" connections)
                  next-elements (map #(get-element-by-id elements (get-in % [:target :element])) next-connections)]
              (condp = element-type
                "contact" (recur (concat (rest current-elements) next-elements)
                                 (conj visited element-id)
                                 (conj instructions (parser/->ILInstruction "LD"
                                                                            (get-in element [:properties :variable])
                                                                            nil)))
                "contact_negated" (recur (concat (rest current-elements) next-elements)
                                         (conj visited element-id)
                                         (conj instructions (parser/->ILInstruction "LDN"
                                                                                    (get-in element [:properties :variable])
                                                                                    nil)))
                "coil" (recur (concat (rest current-elements) next-elements)
                              (conj visited element-id)
                              (conj instructions (parser/->ILInstruction "ST"
                                                                         (get-in element [:properties :variable])
                                                                         nil)))
                "coil_negated" (recur (concat (rest current-elements) next-elements)
                                      (conj visited element-id)
                                      (conj instructions (parser/->ILInstruction "STN"
                                                                                 (get-in element [:properties :variable])
                                                                                 nil)))
                "and" (recur (concat (rest current-elements) next-elements)
                             (conj visited element-id)
                             (let [in2-conn (first (get-input-elements element-id "in2" connections))
                                   in2-element (get-element-by-id elements (get-in in2-conn [:source :element]))]
                               (conj instructions (parser/->ILInstruction "AND"
                                                                          (get-in in2-element [:properties :variable])
                                                                          nil))))
                "or" (recur (concat (rest current-elements) next-elements)
                            (conj visited element-id)
                            (let [in2-conn (first (get-input-elements element-id "in2" connections))
                                  in2-element (get-element-by-id elements (get-in in2-conn [:source :element]))]
                              (conj instructions (parser/->ILInstruction "OR"
                                                                         (get-in in2-element [:properties :variable])
                                                                         nil))))
                "timer_on" (recur (concat (rest current-elements) next-elements)
                                  (conj visited element-id)
                                  (conj instructions (parser/->ILInstruction "TON"
                                                                             (get-in element [:properties :preset])
                                                                             nil)))
                "timer_off" (recur (concat (rest current-elements) next-elements)
                                   (conj visited element-id)
                                   (conj instructions (parser/->ILInstruction "TOF"
                                                                              (get-in element [:properties :preset])
                                                                              nil)))
                "counter_up" (recur (concat (rest current-elements) next-elements)
                                    (conj visited element-id)
                                    (conj instructions (parser/->ILInstruction "CTU"
                                                                               (get-in element [:properties :preset])
                                                                               nil)))
                "counter_down" (recur (concat (rest current-elements) next-elements)
                                      (conj visited element-id)
                                      (conj instructions (parser/->ILInstruction "CTD"
                                                                                 (get-in element [:properties :preset])
                                                                                 nil)))
                (do
                  (println "Unknown LD element type:" element-type)
                  (recur (rest current-elements)
                         (conj visited element-id)
                         instructions))))))))))

;; ---- JSON File Handling for LD Networks ----

(defn ld-to-json
  "Convert LD network to JSON format"
  [ld-network]
  (json/write-str ld-network))

(defn json-to-ld
  "Convert JSON format back to LD network"
  [json-data]
  (let [parsed (json/read-str json-data :key-fn keyword)
        ;; Handle converting :type to :element-type when loading from JSON
        elements (mapv (fn [elem]
                         (let [element-with-correct-fields (if (:type elem)
                                                             (-> elem
                                                                 (assoc :element-type (:type elem))
                                                                 (dissoc :type))
                                                             elem)]
                           (map->LDElement element-with-correct-fields)))
                       (:elements parsed))
        connections (:connections parsed)]
    (->LDNetwork (:id parsed) elements connections)))

(defn save-ld-to-file
  "Save LD network to a JSON file"
  [ld-network filename]
  (spit filename (ld-to-json ld-network)))

(defn load-ld-from-file
  "Load LD network from a JSON file"
  [filename]
  (json-to-ld (slurp filename)))

;; Helper functions for modifying LD networks

(defn add-element
  "Add a new element to an LD network"
  [network element]
  (update network :elements conj element))

(defn remove-element
  "Remove an element from an LD network by ID"
  [network element-id]
  (let [elements (filter #(not= (:id %) element-id) (:elements network))
        connections (filter #(and (not= (get-in % [:source :element]) element-id)
                                  (not= (get-in % [:target :element]) element-id))
                            (:connections network))]
    (assoc network :elements elements :connections connections)))

(defn add-connection
  "Add a new connection between elements in an LD network"
  [network source-id source-port target-id target-port]
  (let [connection {:source {:element source-id :port source-port}
                    :target {:element target-id :port target-port}}]
    (update network :connections conj connection)))

(defn remove-connection
  "Remove a connection from an LD network"
  [network source-id source-port target-id target-port]
  (let [connections (filter #(not (and (= (get-in % [:source :element]) source-id)
                                       (= (get-in % [:source :port]) source-port)
                                       (= (get-in % [:target :element]) target-id)
                                       (= (get-in % [:target :port]) target-port)))
                            (:connections network))]
    (assoc network :connections connections)))

(defn update-element-position
  "Update the position of an element in an LD network"
  [network element-id new-position]
  (let [elements (map (fn [elem]
                        (if (= (:id elem) element-id)
                          (assoc elem :position new-position)
                          elem))
                      (:elements network))]
    (assoc network :elements elements)))

(defn update-element-property
  "Update a property of an element in an LD network"
  [network element-id property-key property-value]
  (let [elements (map (fn [elem]
                        (if (= (:id elem) element-id)
                          (assoc-in elem [:properties property-key] property-value)
                          elem))
                      (:elements network))]
    (assoc network :elements elements)))