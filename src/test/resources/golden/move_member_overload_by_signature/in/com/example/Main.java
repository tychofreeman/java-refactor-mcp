package com.example;

public class Main {
  static String log(String n) {
    return "s" + n;
  }

  static String log(int n) {
    return "i" + n;
  }

  String use() {
    return log("a") + log(1);
  }
}
