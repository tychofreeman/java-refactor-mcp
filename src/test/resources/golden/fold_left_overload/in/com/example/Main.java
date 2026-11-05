package com.example;

public class Main {
  static String show(Object o) {
    return "[" + o + "]";
  }

  static String show(String s) {
    return "<" + s + ">";
  }

  String a(String s) {
    return "[" + s + "]";
  }

  String b(Object o) {
    return "[" + o + "]";
  }
}
