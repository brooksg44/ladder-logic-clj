# LadderLogicClj

A Clojure application for converting between IEC 61131-3 IL (Instruction List) and LD (Ladder Diagram) formats, with simulation capabilities.

## Features

- Convert IL (Instruction List) programs to LD (Ladder Diagram) networks
- Convert LD networks back to IL programs
- Simulate LD networks with real-time variable tracking
- Support for basic logic operations: AND, OR, NOT
- Support for latches, timers, and counters
- Support for basic math operations and comparisons
- JSON-based file format for easy integration with other tools
- Interactive CLI interface

## Requirements

- Java JDK 11+
- Clojure CLI tools

## Project Structure

```
ladder-logic-clj/
├── deps.edn                 # Project dependencies
├── README.md                # This file
├── src/                     # Source code
│   └── com/
│       └── ladder_logic_clj/
│           └── core.clj     # Main application code
└── resources/               # Resources
    └── examples/            # Example files
        ├── sample_il.json   # Sample IL program
        └── sample_ld.json   # Sample LD network
```

## This is currently not working:
```Clojure

(defn usage [options-summary]
  (->> ["LadderLogicClj - IL to LD Converter and Simulator"
        ""
        "Usage: clj -M:run [options]"
        ""
        "Options:"
        options-summary
        ""
        "Examples:"
        "  Convert IL to LD:  clj -M:run -i input.json -o output.json -c il2ld"
        "  Convert LD to IL:  clj -M:run -i input.json -o output.json -c ld2il"
        "  Simulate LD:       clj -M:run -s ladder.json"]
       (str/join \newline)))
```

# Unix/Linux/macOS
./ladder-logic.sh -s ladder-network.json -v

# Windows
ladder-logic.bat -s ladder-network.json -v

## Installation

Clone the repository: