#!/usr/bin/env bash

# Usage: ./run.sh <input_file.java> [output_file.java]
# Example: ./run.sh test.java
# Example: ./run.sh test.java final_output.java

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <input_file.java> [output_file.java]"
    exit 1
fi

INPUT_FILE=$1
OUTPUT_FILE=${2:-"final_output.java"}
TEMP_DIR=$(mktemp -d)

# Clean up temporary intermediate files on exit
trap 'rm -rf "$TEMP_DIR"' EXIT

if [ ! -f "$INPUT_FILE" ]; then
    echo "Error: Input file '$INPUT_FILE' not found."
    exit 1
fi

echo "=================================================="
echo " Starting Multi-Pass Optimizing Compiler Pipeline "
echo "=================================================="

# -----------------------------------------------------------------
# Module 1: Variable Property Checking
# -----------------------------------------------------------------
echo "[Pass 1/4] Running Static Analysis Checks (Module 1)..."
if [ -d "Module1" ]; then
    javac Module1/P1.java || { echo "Compilation failed for Module 1"; exit 1; }
    CHECK_OUTPUT=$(java -cp Module1 P1 < "$INPUT_FILE")
    echo "Static Analysis Result: $CHECK_OUTPUT"
    
    if [ "$CHECK_OUTPUT" != "No issue with variables." ]; then
        echo "Pipeline aborted due to variable property violation."
        exit 1
    fi
else
    echo "Warning: Module1 directory not found, skipping static check."
fi

# -----------------------------------------------------------------
# Module 2: Intermediate Code Generation (BuritoJava -> TACoJava)
# -----------------------------------------------------------------
echo "[Pass 2/4] Generating TACoJava IR (Module 2)..."
javac Module2/P2.java || { echo "Compilation failed for Module 2"; exit 1; }
java -cp Module2 P2 < "$INPUT_FILE" > "$TEMP_DIR/pass2_taco.java" || { echo "Execution failed at Module 2"; exit 1; }

# -----------------------------------------------------------------
# Module 3: Conditional Constant Propagation (TACoJava -> TACoJava2)
# -----------------------------------------------------------------
echo "[Pass 3/4] Performing Constant Propagation & DCE (Module 3)..."
javac Module3/P3.java || { echo "Compilation failed for Module 3"; exit 1; }
java -cp Module3 P3 < "$TEMP_DIR/pass2_taco.java" > "$TEMP_DIR/pass3_taco2.java" || { echo "Execution failed at Module 3"; exit 1; }

# -----------------------------------------------------------------
# Module 4: Function Inlining (FunkyTACoJava -> Final Output)
# -----------------------------------------------------------------
echo "[Pass 4/4] Inlining Monomorphic Functions (Module 4)..."
javac Module4/P4.java || { echo "Compilation failed for Module 4"; exit 1; }
java -cp Module4 P4 < "$TEMP_DIR/pass3_taco2.java" > "$OUTPUT_FILE" || { echo "Execution failed at Module 4"; exit 1; }

echo "=================================================="
echo " Pipeline Complete! Output written to: $OUTPUT_FILE"
echo "=================================================="
