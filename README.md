# Multi-Pass Optimizing Compiler Pipeline

Implementation of a multi-pass optimizing compiler pipeline for **BuritoJava** (a subset of Java) and its Intermediate Representations (**TACoJava**, **TACoJava2**, and **FunkyTACoJava**), developed as part of the **CS6013: Advanced Compiler Design** course at IIT Madras under **Prof. Krishna Nandivada**.

---

## Repository Structure

The repository is organized into four distinct modules corresponding to each stage of the compiler optimization pipeline:

```text
.
├── Module1/               # Assignment 1: Properties of Variables (Uninitialized & Final)
│   ├── P1.java            # Main entry point for Module 1
│   └── ...                # JTB/JavaCC generated visitors and parsers
├── Module2/               # Assignment 2: Intermediate Code Generation (BuritoJava -> TACoJava)
│   ├── P2.java            # Main entry point for Module 2
│   └── ...                # JTB/JavaCC generated visitors and parsers
├── Module3/               # Assignment 3: Conditional Constant Propagation (TACoJava -> TACoJava2)
│   ├── P3.java            # Main entry point for Module 3
│   └── ...                # JTB/JavaCC generated visitors and parsers
├── Module4/               # Assignment 4: Function Inlining (FunkyTACoJava -> FunkyTACoJava)
│   ├── P4.java            # Main entry point for Module 4
│   └── ...                # JTB/JavaCC generated visitors and parsers
├── run.sh                 # Helper script to build and execute modules
└── README.md
