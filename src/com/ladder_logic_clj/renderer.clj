(ns com.ladder-logic-clj.renderer
  "Functions for graphically rendering LD networks using Quil"
  (:require [quil.core :as q]
            [quil.middleware :as m]
            [clojure.string :as str]
            [com.ladder-logic-clj.specs :as specs]))

;; ---- Rendering Constants ----

(def grid-size 60)
(def padding 30)
(def element-width 40)
(def element-height 40)
(def connection-width 2)
(def label-size 10)
(def active-color [0 180 0])
(def inactive-color [100 100 100])
(def power-rail-color [0 0 0])
(def background-color [240 240 240])

;; ---- Helper Functions ----

(defn grid-to-screen
  "Convert grid coordinates to screen coordinates"
  [x y]
  [(+ padding (* x grid-size))
   (+ padding (* y grid-size))])

(defn draw-connection
  "Draw a connection line between two elements"
  [connection elements state-map]
  (let [source-id (get-in connection [:source :element])
        source-port (get-in connection [:source :port])
        target-id (get-in connection [:target :element])
        target-port (get-in connection [:target :port])
        source-element (first (filter #(= (:id %) source-id) elements))
        target-element (first (filter #(= (:id %) target-id) elements))

        ;; Calculate start and end positions
        [source-x source-y] (grid-to-screen
                             (get-in source-element [:position :x])
                             (get-in source-element [:position :y]))
        [target-x target-y] (grid-to-screen
                             (get-in target-element [:position :x])
                             (get-in target-element [:position :y]))

        ;; Get connection state (active or inactive)
        connection-state (get state-map (str source-id "_" source-port) false)]

    ;; Set color based on state
    (apply q/stroke (if connection-state active-color inactive-color))
    (q/stroke-weight connection-width)

    ;; Draw the connection line
    (q/line source-x source-y target-x target-y)))

(defn draw-power-rails
  "Draw the power rails on left and right sides"
  [height]
  (q/stroke-weight 4)
  (apply q/stroke power-rail-color)

  ;; Left rail (L+)
  (q/line padding padding padding (+ padding (* height grid-size)))

  ;; Right rail (L-)
  (let [right-x (+ padding (* (+ 12 2) grid-size))]
    (q/line right-x padding right-x (+ padding (* height grid-size)))))

(defn draw-horizontal-connection
  "Draw a horizontal connection line"
  [x1 x2 y active?]
  (apply q/stroke (if active? active-color inactive-color))
  (q/stroke-weight connection-width)
  (let [[screen-x1 screen-y] (grid-to-screen x1 y)
        [screen-x2 _] (grid-to-screen x2 y)]
    (q/line screen-x1 screen-y screen-x2 screen-y)))

;; ---- Element Drawing Functions ----

(defn draw-contact
  "Draw a normally open contact symbol"
  [x y label active?]
  (let [[screen-x screen-y] (grid-to-screen x y)]
    ;; Draw the connecting lines
    (apply q/stroke (if active? active-color inactive-color))
    (q/stroke-weight connection-width)
    (q/line (- screen-x (/ element-width 2)) screen-y
            (+ screen-x (/ element-width 2)) screen-y)

    ;; Draw the contact symbol
    (q/stroke-weight 1)
    (apply q/stroke (if active? active-color inactive-color))
    (apply q/fill (if active? active-color [240 240 240]))

    ;; Draw the two vertical lines
    (q/line (- screen-x (/ element-width 4)) (- screen-y (/ element-height 4))
            (- screen-x (/ element-width 4)) (+ screen-y (/ element-height 4)))
    (q/line (+ screen-x (/ element-width 4)) (- screen-y (/ element-height 4))
            (+ screen-x (/ element-width 4)) (+ screen-y (/ element-height 4)))

    ;; Draw the label
    (q/fill 0 0 0)
    (q/text-align :center :bottom)
    (q/text-size label-size)
    (q/text label screen-x (- screen-y (/ element-height 2)))))

(defn draw-contact-negated
  "Draw a normally closed contact symbol"
  [x y label active?]
  (let [[screen-x screen-y] (grid-to-screen x y)]
    ;; Draw the connecting lines
    (apply q/stroke (if active? active-color inactive-color))
    (q/stroke-weight connection-width)
    (q/line (- screen-x (/ element-width 2)) screen-y
            (+ screen-x (/ element-width 2)) screen-y)

    ;; Draw the contact symbol
    (q/stroke-weight 1)
    (apply q/stroke (if active? active-color inactive-color))
    (apply q/fill (if active? active-color [240 240 240]))

    ;; Draw the two vertical lines
    (q/line (- screen-x (/ element-width 4)) (- screen-y (/ element-height 4))
            (- screen-x (/ element-width 4)) (+ screen-y (/ element-height 4)))
    (q/line (+ screen-x (/ element-width 4)) (- screen-y (/ element-height 4))
            (+ screen-x (/ element-width 4)) (+ screen-y (/ element-height 4)))

    ;; Draw diagonal line for "normally closed"
    (q/line (- screen-x (/ element-width 4)) (- screen-y (/ element-height 6))
            (+ screen-x (/ element-width 4)) (- screen-y (/ element-height 6)))

    ;; Draw the label
    (q/fill 0 0 0)
    (q/text-align :center :bottom)
    (q/text-size label-size)
    (q/text label screen-x (- screen-y (/ element-height 2)))))

(defn draw-coil
  "Draw a coil (output) symbol"
  [x y label active?]
  (let [[screen-x screen-y] (grid-to-screen x y)]
    ;; Draw the connecting lines
    (apply q/stroke (if active? active-color inactive-color))
    (q/stroke-weight connection-width)
    (q/line (- screen-x (/ element-width 2)) screen-y
            (+ screen-x (/ element-width 2)) screen-y)

    ;; Draw the coil symbol (circle)
    (q/stroke-weight 1)
    (apply q/stroke (if active? active-color inactive-color))
    (apply q/fill (if active? active-color [240 240 240]))
    (q/ellipse screen-x screen-y (/ element-width 2) element-height)

    ;; Draw the label
    (q/fill 0 0 0)
    (q/text-align :center :bottom)
    (q/text-size label-size)
    (q/text label screen-x (- screen-y (/ element-height 2)))))

(defn draw-coil-negated
  "Draw a negated coil (output) symbol"
  [x y label active?]
  (let [[screen-x screen-y] (grid-to-screen x y)]
    ;; Draw the connecting lines
    (apply q/stroke (if active? active-color inactive-color))
    (q/stroke-weight connection-width)
    (q/line (- screen-x (/ element-width 2)) screen-y
            (+ screen-x (/ element-width 2)) screen-y)

    ;; Draw the coil symbol (circle with slash)
    (q/stroke-weight 1)
    (apply q/stroke (if active? active-color inactive-color))
    (apply q/fill (if active? active-color [240 240 240]))
    (q/ellipse screen-x screen-y (/ element-width 2) element-height)

    ;; Draw slash for "negated"
    (q/line (- screen-x (/ element-width 4)) (- screen-y (/ element-height 4))
            (+ screen-x (/ element-width 4)) (+ screen-y (/ element-height 4)))

    ;; Draw the label
    (q/fill 0 0 0)
    (q/text-align :center :bottom)
    (q/text-size label-size)
    (q/text label screen-x (- screen-y (/ element-height 2)))))

(defn draw-function-block
  "Draw a function block with title and ports"
  [x y width height title inputs outputs active?]
  (let [[screen-x screen-y] (grid-to-screen x y)]
    ;; Draw the block
    (q/stroke-weight 1)
    (apply q/stroke (if active? active-color inactive-color))
    (apply q/fill (if active? [200 255 200] [240 240 240]))

    (q/rect (- screen-x (/ (* width element-width) 2))
            (- screen-y (/ (* height element-height) 2))
            (* width element-width)
            (* height element-height))

    ;; Draw the title
    (q/fill 0 0 0)
    (q/text-align :center :center)
    (q/text-size label-size)
    (q/text title screen-x (- screen-y (/ (* height element-height) 3)))

    ;; Draw input ports
    (doseq [[idx input-name] (map-indexed vector inputs)]
      (let [input-y (+ (- screen-y (/ (* height element-height) 3))
                       (* idx (/ (* height element-height) (inc (count inputs)))))]
        (q/text input-name
                (- screen-x (/ (* width element-width) 3))
                input-y)))

    ;; Draw output ports
    (doseq [[idx output-name] (map-indexed vector outputs)]
      (let [output-y (+ (- screen-y (/ (* height element-height) 3))
                        (* idx (/ (* height element-height) (inc (count outputs)))))]
        (q/text output-name
                (+ screen-x (/ (* width element-width) 3))
                output-y)))))

(defn draw-timer
  "Draw a timer function block"
  [x y type preset active?]
  (let [title (case type
                "timer_on" "TON"
                "timer_off" "TOF"
                "timer_pulse" "TP"
                "TIMER")]
    (draw-function-block x y 2 2
                         (str title " " preset)
                         ["IN"] ["Q" "ET"]
                         active?)))

(defn draw-counter
  "Draw a counter function block"
  [x y type preset active?]
  (let [title (case type
                "counter_up" "CTU"
                "counter_down" "CTD"
                "counter_updown" "CTUD"
                "COUNTER")
        inputs (case type
                 "counter_up" ["CU" "R" "PV"]
                 "counter_down" ["CD" "LD" "PV"]
                 "counter_updown" ["CU" "CD" "R" "LD" "PV"]
                 ["IN"])]
    (draw-function-block x y 2 3
                         (str title " " preset)
                         inputs ["Q" "CV"]
                         active?)))

(defn draw-math
  "Draw a math function block"
  [x y type operand active?]
  (let [title (case type
                "add" "ADD"
                "subtract" "SUB"
                "multiply" "MUL"
                "divide" "DIV"
                "MATH")]
    (draw-function-block x y 2 2
                         (str title " " operand)
                         ["IN1" "IN2"] ["OUT"]
                         active?)))

(defn draw-compare
  "Draw a comparison function block"
  [x y type operand active?]
  (let [title (case type
                "greater_than" ">"
                "greater_equal" ">="
                "equal" "="
                "not_equal" "<>"
                "less_equal" "<="
                "less_than" "<"
                "CMP")]
    (draw-function-block x y 2 2
                         (str title " " operand)
                         ["IN1" "IN2"] ["OUT"]
                         active?)))

(defn draw-logic
  "Draw a logic function block (AND, OR)"
  [x y type active?]
  (let [title (case type
                "and" "AND"
                "or" "OR"
                "not" "NOT"
                "LOGIC")]
    (draw-function-block x y 1.5 1.5
                         title
                         (if (= type "not") ["IN"] ["IN1" "IN2"])
                         ["OUT"]
                         active?)))

;; ---- Main Element Drawing Dispatch ----

(defn draw-element
  "Draw an LD element based on its type"
  [element state-map]
  (let [type (:type element)
        id (:id element)
        x (get-in element [:position :x])
        y (get-in element [:position :y])
        variable (get-in element [:properties :variable] "")
        preset (get-in element [:properties :preset] "")
        active? (get state-map id false)]

    (case type
      "contact" (draw-contact x y variable active?)
      "contact_negated" (draw-contact-negated x y variable active?)
      "coil" (draw-coil x y variable active?)
      "coil_negated" (draw-coil-negated x y variable active?)
      "and" (draw-logic x y "and" active?)
      "or" (draw-logic x y "or" active?)
      "not" (draw-logic x y "not" active?)
      "timer_on" (draw-timer x y "timer_on" preset active?)
      "timer_off" (draw-timer x y "timer_off" preset active?)
      "timer_pulse" (draw-timer x y "timer_pulse" preset active?)
      "counter_up" (draw-counter x y "counter_up" preset active?)
      "counter_down" (draw-counter x y "counter_down" preset active?)
      "counter_updown" (draw-counter x y "counter_updown" preset active?)
      "add" (draw-math x y "add" (get-in element [:properties :operand] "") active?)
      "subtract" (draw-math x y "subtract" (get-in element [:properties :operand] "") active?)
      "multiply" (draw-math x y "multiply" (get-in element [:properties :operand] "") active?)
      "divide" (draw-math x y "divide" (get-in element [:properties :operand] "") active?)
      "greater_than" (draw-compare x y "greater_than" (get-in element [:properties :operand] "") active?)
      "greater_equal" (draw-compare x y "greater_equal" (get-in element [:properties :operand] "") active?)
      "equal" (draw-compare x y "equal" (get-in element [:properties :operand] "") active?)
      "not_equal" (draw-compare x y "not_equal" (get-in element [:properties :operand] "") active?)
      "less_equal" (draw-compare x y "less_equal" (get-in element [:properties :operand] "") active?)
      "less_than" (draw-compare x y "less_than" (get-in element [:properties :operand] "") active?)

      ;; Default case - unknown element type
      (do
        (println "Unknown element type for drawing:" type)
        (draw-function-block x y 1 1 type [] [] false)))))

;; ---- Main Quil Drawing Functions ----

(defn calculate-network-dimensions
  "Calculate the required dimensions for the network"
  [network]
  (let [elements (:elements network)
        max-x (apply max (map #(get-in % [:position :x]) elements))
        max-y (apply max (map #(get-in % [:position :y]) elements))]
    [(+ max-x 3) (+ max-y 1)]))

(defn setup-renderer
  "Setup function for Quil sketch"
  [network variables state-map]
  (let [[width height] (calculate-network-dimensions network)
        canvas-width (+ (* width grid-size) (* 2 padding))
        canvas-height (+ (* height grid-size) (* 2 padding))]
    (q/frame-rate 30)
    (q/color-mode :rgb)
    (q/text-font (q/create-font "Arial" 12 true))

    ;; Return initial state
    {:network network
     :variables variables
     :state-map @state-map
     :dimensions [width height]}))

(defn update-renderer
  "Update function for Quil sketch"
  [state]
  (let [network (:network state)
        variables (:variables state)
        state-map (:state-map state)]
    ;; This function can update the simulation state if you want animation
    ;; For now, we just pass along the current state
    state))

(defn draw-renderer
  "Draw function for Quil sketch"
  [{:keys [network variables state-map dimensions]}]
  (apply q/background background-color)

  ;; Draw power rails
  (draw-power-rails (second dimensions))

  ;; Draw connections
  (doseq [connection (:connections network)]
    (draw-connection connection (:elements network) state-map))

  ;; Draw elements
  (doseq [element (:elements network)]
    (draw-element element state-map)))

(defn create-renderer
  "Create a Quil sketch to render an LD network"
  [network variables state-map]
  (let [[width height] (calculate-network-dimensions network)
        canvas-width (+ (* width grid-size) (* 2 padding))
        canvas-height (+ (* height grid-size) (* 2 padding))]
    (q/sketch
     :title "Ladder Logic Diagram"
     :size [canvas-width canvas-height]
     :setup (fn [] (setup-renderer network variables state-map))
     :update update-renderer
     :draw draw-renderer
     :features [:keep-on-top]
     :middleware [m/fun-mode])))

;; ---- Public API ----

(defn render-network
  "Render an LD network with current state"
  [network variables state-map]
  (create-renderer network @variables state-map))

(defn export-network-as-image
  "Export the LD network as an image file"
  [network variables state-map filename]
  (let [[width height] (calculate-network-dimensions network)
        canvas-width (+ (* width grid-size) (* 2 padding))
        canvas-height (+ (* height grid-size) (* 2 padding))
        sketch (q/sketch
                :title "Ladder Logic Diagram Export"
                :size [canvas-width canvas-height]
                :setup (fn []
                         (setup-renderer network variables state-map)
                         (q/save filename)
                         (q/exit))
                :features [:keep-on-top]
                :middleware [m/fun-mode])]
    sketch))