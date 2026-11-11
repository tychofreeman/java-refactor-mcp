package com.example;

import java.io.IOException;
import java.io.PrintStream;

class Renamer {
  interface Move {
    void apply(String r) throws IOException;
  }

  private final PrintStream out = System.out;
  private final PrintStream err = System.err;
  private final Move move = r -> {};

  boolean carryOut(String r) {
    out.println(r + " -> done");
    try {
      move.apply(r);
    } catch (IOException e) {
      err.println("could not rename " + r + ": " + e);
      return false;
    }
    return true;
  }
}
