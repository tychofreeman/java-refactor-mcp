package com.example;

public class Out {
  String label() {
    return "out";
  }

  Act make() {
    Act a = () -> label();
    return a;
  }
}
