# Multi-Pass Optimizing Compiler Pipeline

A complete multi-pass optimizing compiler pipeline built for **BuritoJava** (a subset of Java) and its intermediate representations (**TACoJava**, **TACoJava2**, and **FunkyTACoJava**). Developed for the **CS6013: Advanced Compiler Design** course at IIT Madras under **Prof. Krishna Nandivada**.

---

## Table of Contents
- [Overview](#overview)
- [Repository Structure](#repository-structure)
- [Prerequisites](#prerequisites)
- [How to Build and Run](#how-to-build-and-run)
  - [Option A: Automated Execution Script (`run.sh`)](#option-a-automated-execution-script-runsh)
  - [Option B: Manual Compilation & Execution](#option-b-manual-compilation--execution)

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
