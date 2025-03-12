#!/bin/bash
# LadderLogicClj execution script

# Check if Clojure CLI is installed
if ! command -v clj &> /dev/null; then
    echo "Error: Clojure CLI (clj) not found. Please install it first."
    exit 1
fi

# Pass all arguments to the Clojure application
clj -M:run "$@"