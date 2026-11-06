package com.example;

class Log {
  boolean say(String s) {
    if (s.isEmpty()) {
      return false;
    }
    System.out.println(s);
    return false;
  }
}
