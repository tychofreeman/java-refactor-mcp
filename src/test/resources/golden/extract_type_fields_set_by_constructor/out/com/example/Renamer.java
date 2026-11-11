package com.example;

import java.io.PrintStream;

final class Renamer {
  private final TextReport report;
  private final int retries;

  Renamer(PrintStream out, PrintStream err, int retries) {
    this.report = new TextReport(out, err);
    this.retries = retries;
  }

  Renamer(PrintStream out) {
    this(out, System.err, 1);
  }

  void report(String r) {
    report.out.println("renamed " + r);
    if (retries > 1) {
      report.err.println("retried " + r);
    }
  }
}
