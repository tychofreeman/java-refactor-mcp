package com.example;

import java.io.PrintStream;

class Renamer {
  private final PrintStream err;

  Renamer(PrintStream err) {
    this.err = err;
  }

  void report(String r) {
    String err = "renamed " + r;
    this.err.println(err);
  }

  static final int LIMIT = 3;

  static int clamp(int n, int max) {
    return Math.min(n, max);
  }

  void run(String r) {
    report(r);
    System.out.println(clamp(5, LIMIT) + clamp(1, Renamer.LIMIT));
  }
}
