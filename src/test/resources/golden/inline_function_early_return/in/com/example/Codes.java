package com.example;

public class Codes {
  String tail(String code) {
    return last(code, 4);
  }

  String padded(String code, int width) {
    if (code.isEmpty()) {
      return "";
    }
    return last("0000" + code, width);
  }

  private static String last(String s, int n) {
    if (s.length() <= n) {
      return s;
    }
    return s.substring(s.length() - n);
  }
}
