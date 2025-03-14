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

    ;; Return initial state - NO continuous update by default
    {:network network
     :variables variables
     :state-map @state-map
     :dimensions [width height]}))

(defn update-renderer
  "Update function for Quil sketch - SIMPLIFIED"
  [state]
  ;; Just return the state unchanged
  ;; The simulator will update the atoms that the renderer reads from
  state)

(defn draw-renderer
  "Draw function for Quil sketch"
  [{:keys [network variables state-map dimensions]}]
  (apply q/background background-color)

  ;; Draw power rails
  (draw-power-rails (second dimensions))

  ;; Draw connections
  (doseq [connection (:connections network)]
    (draw-connection connection (:elements network) @state-map))

  ;; Draw elements
  (doseq [element (:elements network)]
    (draw-element element @state-map)))

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