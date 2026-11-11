package com.example;

import java.io.PrintStream;

final class Renamer {
  private final PrintStream out;
  private final PrintStream err;
  private final int retries;

  Renamer(PrintStream out, PrintStream err, int retries) {
    this.out = out;
    this.err = wrap(err);
    this.retries = retries;
  }

  Renamer(PrintStream out) {
    this(out, System.err, 1);
  }

  static PrintStream wrap(PrintStream p) {
    return p;
  }

  void report(String r) {
    out.println("renamed " + r);
    if (retries > 1) {
      err.println("retried " + r);
    }
  }
}
