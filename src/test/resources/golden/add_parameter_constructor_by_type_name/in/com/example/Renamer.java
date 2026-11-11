package com.example;

import java.io.PrintStream;

class Renamer {
  final PrintStream err;

  Renamer(PrintStream err) {
    this.err = err;
  }

  static Renamer make() {
    return new Renamer(System.err);
  }
}
