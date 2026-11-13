package com.example;

class Use {
  void run() throws java.io.IOException {
    Greeter g = new Greeter(Greeter::greetLoud);
    g.greet("a");
  }
}
