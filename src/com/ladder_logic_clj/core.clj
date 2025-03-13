(ns com.ladder-logic-clj.core
  "LadderLogicClj - Main application for IL to LD conversion and simulation"
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.string :as str]
            [clojure.data.json :as json]
            [clojure.spec.alpha :as s]
            [com.ladder-logic-clj.specs :as specs]
            [com.ladder-logic-clj.parser :as parser]
            [com.ladder-logic-clj.converter :as converter]
            [com.ladder-logic-clj.simulator :as simulator]
            [com.ladder-logic-clj.renderer :as renderer]) ; Added renderer namespace
  (:gen-class))

;; ---- CLI Interface ----

(def cli-options
  [["-i" "--input FILE" "Input file"]
   ["-o" "--output FILE" "Output file"]
   ["-c" "--convert TYPE" "Conversion type (il2ld or ld2il)"]
   ["-s" "--simulate FILE" "Simulate LD network from file"]
   ["-v" "--visual" "Enable graphical visualization"]
   ["-e" "--export FILE" "Export LD diagram to image file"]
   ["-h" "--help"]])

(defn usage [options-summary]
  (->> ["LadderLogicClj - IL to LD Converter and Simulator"
        ""
        "Usage: clj -M:run [options]"
        ""
        "Options:"
        options-summary
        ""
        "Examples:"
        "  Convert IL to LD:    clj -M:run -i input.json -o output.json -c il2ld"
        "  Convert LD to IL:    clj -M:run -i input.json -o output.json -c ld2il"
        "  Simulate LD:         clj -M:run -s ladder.json"
        "  Visual Simulation:   clj -M:run -s ladder.json -v"
        "  Export LD Diagram:   clj -M:run -s ladder.json -e diagram.png"]
       (str/join \newline)))

(defn error-msg [errors]
  (str "The following errors occurred while parsing your command:\n\n"
       (str/join \newline errors)))

(defn validate-args
  "Validate command line arguments"
  [args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    #_(println "DEBUG: Parsed options:" options) ;; Add debugging
    #_(println "DEBUG: Parsed arguments:" arguments)
    #_(println "DEBUG: Errors:" errors)
    (cond
      (:help options)
      {:exit-message (usage summary) :ok? true}

      errors
      {:exit-message (error-msg errors)}

      ;; Conversion mode: requires -i, -o, and -c
      (and (:input options) (:output options) (:convert options))
      (if (contains? #{"il2ld" "ld2il"} (:convert options))
        {:options options :mode :convert}
        {:exit-message "Error: Convert type must be 'il2ld' or 'ld2il'"})

      ;; Simulation mode: requires -s
      (:simulate options)
      (cond
        ;; Export mode: requires -s and -e
        (:export options)
        {:options options :mode :export}

        ;; Visual simulation: requires -s and -v
        (:visual options)
        {:options options :mode :visual-simulate}

        ;; Regular simulation: just -s
        :else
        {:options options :mode :simulate}) :else
      {:exit-message (usage summary)})))

(defn exit [status msg]
  (println msg)
  (System/exit status))

(defn convert-file
  "Convert a file between IL and LD formats"
  [options]
  (let [input-file (:input options)
        output-file (:output options)
        convert-type (:convert options)]
    (case convert-type
      "il2ld" (let [il-instructions (parser/load-il-from-file input-file)
                    ld-network (converter/il-to-ld-network il-instructions)]
                (converter/save-ld-to-file ld-network output-file)
                (println "Converted IL to LD and saved to" output-file))

      "ld2il" (let [ld-network (converter/load-ld-from-file input-file)
                    il-instructions (converter/trace-ld-network ld-network)]
                (parser/save-il-to-file il-instructions output-file)
                (println "Converted LD to IL and saved to" output-file))

      (println "Unknown conversion type:" convert-type))))

(defn run-simulation
  "Run a simulation of the LD network"
  [options]
  (let [ld-file (:simulate options)
        ld-network (converter/load-ld-from-file ld-file)]

    (println "Starting simulation of LD network from" ld-file)
    (simulator/run-interactive-simulation ld-network)))

(defn run-visual-simulation
  "Run a simulation of the LD network with graphical visualization"
  [options]
  (let [ld-file (:simulate options)
        ld-network (converter/load-ld-from-file ld-file)]

    (println "Starting visual simulation of LD network from" ld-file)
    (simulator/run-interactive-simulation ld-network :with-visualization true)))

(defn export-ld-diagram
  "Export the LD network as an image"
  [options]
  (let [ld-file (:simulate options)
        export-file (:export options)
        ld-network (converter/load-ld-from-file ld-file)
        variables (atom (simulator/initialize-variables))
        state-map (atom {})]

    (println "Exporting LD network from" ld-file "to" export-file)

    ;; Call export function
    (renderer/export-network-as-image ld-network variables state-map export-file)

    (println "Export complete")))

(defn -main
  "Main entry point"
  [& args]
  (let [{:keys [options exit-message ok? mode]} (validate-args args)]
    (if exit-message
      (exit (if ok? 0 1) exit-message)
      (case mode
        :simulate (run-simulation options)
        :visual-simulate (run-visual-simulation options)
        :export (export-ld-diagram options)
        :convert (convert-file options)
        (exit 1 "Unknown mode")))))

;; ---- REPL Interface Examples ----

(comment
  ;; Example IL program
  (def sample-il "
  LD X1
  AND X2
  OR X3
  ST Y1
  ")

  ;; Parse IL string to instructions
  (def il-instructions (parser/parse-il-program sample-il))

  ;; Convert IL to LD
  (def ld-network (converter/il-to-ld-network il-instructions))

  ;; Convert LD back to IL
  (def new-il (converter/trace-ld-network ld-network))

  ;; Save and load files
  (parser/save-il-to-file il-instructions "il-program.json")
  (converter/save-ld-to-file ld-network "ld-network.json")

  ;; Load from files
  (def loaded-il (parser/load-il-from-file "il-program.json"))
  (def loaded-ld (converter/load-ld-from-file "ld-network.json"))

  ;; Run simulation
  (def variables (atom [(simulator/create-variable "X1" "BOOL" false)
                        (simulator/create-variable "X2" "BOOL" true)
                        (simulator/create-variable "X3" "BOOL" false)
                        (simulator/create-variable "Y1" "BOOL" false)]))

  (def state-map (atom {}))

  (simulator/run-simulation-cycle ld-network variables state-map)

  ;; Run interactive simulation
  (simulator/run-interactive-simulation ld-network)

  ;; Modify LD network
  (def modified-ld (-> ld-network
                       (converter/update-element-position
                        (get-in ld-network [:elements 0 :id])
                        {:x 10 :y 5})
                       (converter/update-element-property
                        (get-in ld-network [:elements 1 :id])
                        :variable "X4"))))