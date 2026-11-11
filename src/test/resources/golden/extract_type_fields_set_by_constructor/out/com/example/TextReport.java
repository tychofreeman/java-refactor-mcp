package com.example;

import java.io.PrintStream;

class TextReport {
  final PrintStream out;
  final PrintStream err;

  TextReport(PrintStream out, PrintStream err) {
    this.out = out;
    this.err = err;
  }
}
