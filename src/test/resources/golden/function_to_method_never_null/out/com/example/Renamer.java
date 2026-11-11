package com.example;

import java.io.PrintStream;

public class Renamer {
  final PrintStream out;
  final PrintStream err;

  Renamer(PrintStream out, PrintStream err) {
    this.out = out;
    this.err = err;
  }

  boolean carryOut(String r) {
    this.out.println(r);
    return true;
  }

  boolean again(String r) {
    return carryOut(r);
  }
}
