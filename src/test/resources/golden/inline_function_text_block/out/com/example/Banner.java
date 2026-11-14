package com.example;

public class Banner {
  void run(boolean on) {
    if (on) {
      String s = """
abc
    def
""";
      System.out.print(s + 1);
    }
  }
}
