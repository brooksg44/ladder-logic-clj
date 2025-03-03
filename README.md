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

## Installation

Clone the repository: