;; Replace these functions in renderer.clj

(defn setup-renderer
  "Setup function for Quil sketch"
  [network variables state-map]
  (let [[width height] (calculate-network-dimensions network)
        canvas-width (+ (* width grid-size) (* 2 padding))
        canvas-height (+ (* height grid-size) (* 2 padding))]
    (q/frame-rate 30)
    (q/color-mode :rgb)
    (q/text-font (q/create-font "Arial" 12 true))

    ;; Return initial state with continuous rendering enabled
    {:network network
     :variables variables
     :state-map @state-map
     :dimensions [width height]
     :last-update-time (System/currentTimeMillis)
     :update-interval 200 ; 200ms update interval (5 fps)
     :continuous-update true})) ; Continuous updates enabled by default

(defn update-renderer
  "Update function for Quil sketch - includes continuous simulation"
  [state]
  (let [network (:network state)
        variables (:variables state)
        state-map (:state-map state)
        current-time (System/currentTimeMillis)
        last-update-time (:last-update-time state)]
    
    ;; Run simulation at the specified interval if continuous update is enabled
    (if (and (:continuous-update state) 
             (> (- current-time last-update-time) (:update-interval state)))
      (try
        ;; Call simulator function using explicit do to sequence operations
        (do
          ;; Run the simulation (ignoring the return value)
          (require '[com.ladder-logic-clj.simulator :as simulator])
          ((resolve 'simulator/run-simulation-cycle) network variables state-map)
          
          ;; Return updated state with new timestamp
          (assoc state :last-update-time current-time))
        (catch Exception e
          (println "Error in simulation update:" (.getMessage e))
          state))
      ;; No update needed, return original state
      state)))

(defn key-pressed
  "Handle key press events"
  [state event]
  (case (:key event)
    ;; Space key toggles continuous update
    :space (update state :continuous-update not)
    ;; Enter key forces a single update
    :enter (let [network (:network state)
                 variables (:variables state)
                 state-map (:state-map state)]
             (try
               ;; Perform a single simulation step
               (do
                 (require '[com.ladder-logic-clj.simulator :as simulator])
                 ((resolve 'simulator/run-simulation-cycle) network variables state-map)
                 ;; Return updated state with new timestamp
                 (assoc state :last-update-time (System/currentTimeMillis)))
               (catch Exception e
                 (println "Error in manual simulation step:" (.getMessage e))
                 state)))
    ;; Default case - no change
    state))

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
     :key-pressed key-pressed
     :features [:keep-on-top]
     :middleware [m/fun-mode])))