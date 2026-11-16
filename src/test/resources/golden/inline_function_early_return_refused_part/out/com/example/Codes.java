package com.example;

public class Codes {
  String masked(String code) {
    return "****" + last(code, 4);
  }

  private static String last(String s, int n) {
    if (s.length() <= n) {
      return s;
    }
    return s.substring(s.length() - n);
  }
}
