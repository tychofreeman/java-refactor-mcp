package com.example;

public class Out {
  int count = 1;

  Act make() {
    Act a = () -> count;
    return a;
  }
}
