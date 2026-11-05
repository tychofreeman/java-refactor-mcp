package com.example;

class Basket {
  static Runnable task() {
    return new Runnable() {
      @Override
      public void run() {
        helper(1);
      }
    };
  }

  static void helper(int i) {
    throw new UnsupportedOperationException("not implemented");
  }
}
