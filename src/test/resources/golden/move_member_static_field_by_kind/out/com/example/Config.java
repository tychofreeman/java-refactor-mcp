package com.example;

class Config {
  static final int MIN = 1;

  /** The most items in a cart. */
  static final int MAX = 10;

  static int span() {
    return 2 * MIN;
  }
}
