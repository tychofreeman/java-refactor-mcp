package com.example;

class LoudGreeter extends Greeter {
  LoudGreeter(int times) {
    super();
  }

  @Override
  void greet(String name) throws java.io.IOException {
    System.out.println("HELLO " + name.toUpperCase());
  }
}
