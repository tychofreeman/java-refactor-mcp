package com.example;

public class Label {
  private String text = "none";

  public String show(boolean done) {
    if (done) {
      return text;
    } else {
      String text = "local";
      log(text);
    }
    return text;
  }

  private void log(String s) {}
}
