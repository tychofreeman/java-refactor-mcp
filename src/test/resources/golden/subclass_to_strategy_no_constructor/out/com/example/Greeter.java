package com.example;

class Greeter {
  interface Greet {
    void greet(String name) throws java.io.IOException;
  }

  private final Greet greet;

  Greeter(Greet greet) {
    this.greet = greet;
  }

  Greeter() {
    this(Greeter::defaultGreet);
  }

  void greet(String name) throws java.io.IOException {
    greet.greet(name);
  }

  private static void defaultGreet(String name) throws java.io.IOException {
    System.out.println("hello " + name);
  }

  static void greetLoud(String name) throws java.io.IOException {
    System.out.println("HELLO " + name.toUpperCase());
  }
}
