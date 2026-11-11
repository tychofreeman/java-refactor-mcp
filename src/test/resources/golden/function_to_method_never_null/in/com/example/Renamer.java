package com.example;

import java.io.PrintStream;

public class Renamer {
  final PrintStream out;
  final PrintStream err;

  Renamer(PrintStream out, PrintStream err) {
    this.out = out;
    this.err = err;
  }

  static boolean carryOut(Renamer renamer, String r) {
    renamer.out.println(r);
    return true;
  }

  boolean again(String r) {
    return carryOut(this, r);
  }
}
