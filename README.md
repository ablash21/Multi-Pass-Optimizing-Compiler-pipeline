# Multi-Pass Optimizing Compiler Pipeline

A complete multi-pass optimizing compiler pipeline built for **BuritoJava** (a subset of Java) and its intermediate representations (**TACoJava**, **TACoJava2**, and **FunkyTACoJava**). Developed for the **CS6013: Advanced Compiler Design** course at IIT Madras under **Prof. Krishna Nandivada**.

---

## Table of Contents
- [Overview](#overview)
- [Repository Structure](#repository-structure)
- [How to Build and Run]
- [Sample inputs]
- -[Other Comments]

---

## Overview

This project implements static analysis, intermediate language translation, static optimizations, and call-site transformations using **JavaCC** (Java Compiler Compiler) and **JTB** (Java Tree Builder) via the Visitor Design Pattern.

The compiler operates in a 4-stage pipeline:
1. **Static Analysis Pass:** Detects variable safety violations (uninitialized usage, final reassignment).
2. **IR Lowering:** Translates high-level BuritoJava into Three-Address Code IR (TACoJava).
3. **Dataflow & Control Optimizations:** Constant propagation, Call-Graph construction via CHA, and dead/unreachable code elimination.
4. **Call-Site Optimization:** Value-context sensitive function inlining for monomorphic methods.

---

## Repository Structure

```text
.
├── Module1/               # Static Variable Analysis Pass
│   ├── P1.java            # Main entry point for Module 1
│   └── ...                # JTB/JavaCC generated parser & visitors
├── Module2/               # Intermediate Code Generation (BuritoJava -> TACoJava)
│   ├── P2.java            # Main entry point for Module 2
│   └── ...                # JTB/JavaCC generated parser & visitors
├── Module3/               # Conditional Constant Propagation (TACoJava -> TACoJava2)
│   ├── P3.java            # Main entry point for Module 3
│   └── ...                # JTB/JavaCC generated parser & visitors
├── Module4/               # Monomorphic Function Inlining (FunkyTACoJava -> FunkyTACoJava)
│   ├── P4.java            # Main entry point for Module 4
│   └── ...                # JTB/JavaCC generated parser & visitors
├── run.sh                 # Automation shell script
└── README.md

## Build and Run

### Using the Compilation Script

The complete compiler pipeline consists of **four stages**. To compile and run an input Java program through all four stages, use the following script:

```bash
./compiler.sh inputProgram.java [output.java]
```

The script expects both `javac` and `java` to be installed and available in the system `PATH`. If the output file is not specified, the script will use its default output configuration.

### Running Each Stage Manually

Alternatively, each compiler stage can be executed manually. For module `x`, compile the corresponding `Px.java` file and then run it using:

```bash
javac Px.java && java P.java < input.java > output.java
```

Here, `x` denotes the **module number**. The output of one stage can be used as the input to the next stage to obtain the final transformed Java program.

### Sample inputs

Additional  sample inputs can be found on the course webpage:

[CS6013 Test Cases](https://www.cse.iitm.ac.in/~krishna/cs6013/subsets.html?utm_source=chatgpt.com)

## Other Comments

1. The Bash scripts used in this project were generated with the assistance of LLMs.
2. **Modules 3 and 4** were developed with the assistance of LLMs, as permitted/expected as part of the course requirements.

