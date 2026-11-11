package com.example;

import java.io.PrintStream;

class Renamer {
  private final PrintStream out;

  Renamer(PrintStream out) {
    this.out = out;
  }

  /**
   * Reports one rename.
   *
   * @param r the rename
   */
  void report(String r) {
    out.println("renamed " + r);
  }

  void run(String r) {
    report(r);
    report(r);
  }
}
