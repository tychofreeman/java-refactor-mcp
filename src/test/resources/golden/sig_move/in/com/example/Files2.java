package com.example;

class Files2 {
  void copy(String from, String to) {
    System.out.println(from + to);
  }

  void run(String a, String b, String y) {
    copy(a, b);
    copy("x", y);
  }
}
