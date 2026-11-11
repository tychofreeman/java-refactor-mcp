package com.example;

import java.io.PrintStream;

class Renamer {
  private final PrintStream err;

  Renamer(PrintStream err) {
    this.err = err;
  }

  void report(PrintStream to, String r) {
    String err = "renamed " + r;
    to.println(err);
  }

  static final int LIMIT = 3;

  static int clamp(int n, int max) {
    return Math.min(n, max);
  }

  void run(String r) {
    report(err, r);
    System.out.println(clamp(5, LIMIT) + clamp(1, Renamer.LIMIT));
  }
}
