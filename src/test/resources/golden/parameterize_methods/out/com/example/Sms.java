package com.example;

class Sms {
  private final String text;

  Sms(String text) {
    this.text = text;
  }

  String shortForm() {
    return fit(20, 17, "...");
  }

  String tinyForm() {
    return fit(8, 7, "~");
  }

  private String fit(int limit, int keep, String mark) {
    String cut = text.length() > limit ? text.substring(0, keep) : text;
    return cut + mark;
  }
}
