package com.example;

class Files2 {
  void copy(String to, String from) {
    System.out.println(from + to);
  }

  void run(String a, String b, String y) {
    copy(b, a);
    copy(y, "x");
  }
}
