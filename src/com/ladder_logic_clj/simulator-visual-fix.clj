;; Replace this function in simulator.clj

(defn run-interactive-simulation
  "Run an interactive simulation of an LD network"
  [network & {:keys [with-visualization] :or {with-visualization false}}]
  (let [variables (initialize-variables)
        state-map (initialize-state-map)]

    (println "Starting interactive simulation")
    (println "Type 'exit' to quit, 'set VAR VALUE' to set a variable,")
    (println "'visual' to toggle visualization, or press Enter to run one cycle.")
    (println "In visualization window: SPACE to toggle continuous updates, ENTER for single step")

    ;; Start visualization if requested
    (when with-visualization
      (require '[com.ladder-logic-clj.renderer :as renderer])
      ((resolve 'renderer/render-network) network variables state-map))

    (loop [visualize with-visualization]
      (println "\nCurrent variables:")
      (doseq [var @variables]
        (println (str (:name var) " = " (:value var))))

      (print "> ")
      (flush)
      (let [input (read-line)]
        (cond
          (= input "exit")
          (println "Simulation ended.")

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
            ;; Just run the regular simulation cycle - visualization will update itself
            (simulate-ld-network network variables state-map)
            (recur visualize)))))))