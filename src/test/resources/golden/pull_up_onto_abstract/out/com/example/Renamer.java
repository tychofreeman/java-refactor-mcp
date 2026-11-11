package com.example;

public abstract class Renamer {
  /** Carries out one rename. */
  boolean carryOut(String rename) {
    System.out.println("would " + rename);
    return true;
  }

  boolean all(String a, String b) {
    return carryOut(a) && carryOut(b);
  }
}
