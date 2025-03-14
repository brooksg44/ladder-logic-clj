;; Replace this function in simulator.clj

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