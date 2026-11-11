package com.example;

import java.io.PrintStream;

final class Renamer {
  private final PrintStream out;
  private final PrintStream err;
  private final int retries;

  Renamer(PrintStream out, PrintStream err, int retries) {
    this.out = out;
    this.err = err;
    this.retries = retries;
  }

  Renamer(PrintStream out) {
    this(out, System.err, 1);
  }

  void report(String r) {
    out.println("renamed " + r);
    if (retries > 1) {
      err.println("retried " + r);
    }
  }
}
