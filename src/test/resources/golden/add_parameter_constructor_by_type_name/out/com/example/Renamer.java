package com.example;

import java.io.PrintStream;

class Renamer {
  final PrintStream err;

  Renamer(PrintStream err, String mode) {
    this.err = err;
  }

  static Renamer make() {
    return new Renamer(System.err, "dry");
  }
}
