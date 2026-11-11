package com.example;

public abstract class Renamer {
  /** Carries out one rename. */
  abstract boolean carryOut(String r);

  boolean all(String a, String b) {
    return carryOut(a) && carryOut(b);
  }
}
