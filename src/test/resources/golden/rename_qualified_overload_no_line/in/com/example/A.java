package com.example;

public class A {
  String show(int x) {
    return "n" + x;
  }

  String show(String s) {
    return s;
  }

  String both() {
    return show(1) + show("a");
  }
}
