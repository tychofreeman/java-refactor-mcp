package com.example;

class Use {
  void run() throws java.io.IOException {
    Greeter g = new LoudGreeter(2);
    g.greet("a");
  }
}
