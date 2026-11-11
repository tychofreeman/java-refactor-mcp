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
   * @param out where to
   * @param r the rename
   */
  void report(PrintStream out, String r) {
    out.println("renamed " + r);
  }

  void run(String r) {
    report(out, r);
    report(this.out, r);
  }
}
