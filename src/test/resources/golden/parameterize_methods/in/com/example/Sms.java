package com.example;

class Sms {
  private final String text;

  Sms(String text) {
    this.text = text;
  }

  String shortForm() {
    String cut = text.length() > 20 ? text.substring(0, 17) : text;
    return cut + "...";
  }

  String tinyForm() {
    String cut = text.length() > 8 ? text.substring(0, 7) : text;
    return cut + "~";
  }
}
