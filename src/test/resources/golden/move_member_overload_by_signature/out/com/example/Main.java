package com.example;

public class Main {
  static String log(int n) {
    return "i" + n;
  }

  String use() {
    return Renamer.log("a") + log(1);
  }
}
