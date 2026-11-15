package com.example;

public class A {
  String showNumber(int x) {
    return "n" + x;
  }

  String show(String s) {
    return s;
  }

  String both() {
    return showNumber(1) + show("a");
  }
}
