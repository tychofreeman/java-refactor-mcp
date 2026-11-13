package com.example;

class Base {
  Base(String name) {}

  static Base make() {
    return new Base("anon") {
      @Override
      public String toString() {
        return "anon";
      }
    };
  }
}
