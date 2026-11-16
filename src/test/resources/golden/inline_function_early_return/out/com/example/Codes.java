package com.example;

public class Codes {
  String tail(String code) {
    if (code.length() <= 4) {
      return code;
    }
    return code.substring(code.length() - 4);
  }

  String padded(String code, int width) {
    if (code.isEmpty()) {
      return "";
    }
    if (("0000" + code).length() <= width) {
      return "0000" + code;
    }
    return ("0000" + code).substring(("0000" + code).length() - width);
  }
}
