package com.example;

class Base {
  Base(String name, int limit) {}

  static Base make() {
    return new Base("anon", 0) {
      @Override
      public String toString() {
        return "anon";
      }
    };
  }
}
