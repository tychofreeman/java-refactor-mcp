package com.example;

class Log {
  void say(String s) {
    if (s.isEmpty()) {
      return;
    }
    System.out.println(s);
  }
}
