package com.example;

public class Outer {
  void log(String s) {
    System.out.println(s);
  }

  class Inner {
    void run(String m) {
      log("start");
      System.out.print(m);
    }
  }
}
