package com.example;

public class Price {
  int rate = 2;

  int charge(int a) {
    Runnable r = new Runnable() {
      public void run() {}
    };
    return a * rate;
  }
}

class Fees {
}
