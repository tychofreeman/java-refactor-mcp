package com.example;

public class Banner {
  static void show(int n) {
    String s = """
abc
    def
""";
    System.out.print(s + n);
  }

  void run(boolean on) {
    if (on) {
      show(1);
    }
  }
}
