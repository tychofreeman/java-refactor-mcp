package com.example;

public class Price {
  int rate = 2;

  int charge(int a) {
    return a * rate;
  }

  int total(int a) {
    return charge(a) + 1;
  }
}
