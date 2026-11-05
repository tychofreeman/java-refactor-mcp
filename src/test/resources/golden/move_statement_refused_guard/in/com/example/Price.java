package com.example;

public class Price {
  int total;

  int f(boolean done) {
    if (done) {
      return 0;
    }
    total++;
    return total;
  }
}
