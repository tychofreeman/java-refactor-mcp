package com.example;

public class Tick {
  Runnable make(StringBuilder out) {
    Runnable r = new Runnable() {
      @Override
      public void run() {
        out.append("x");
      }
    };
    return r;
  }
}
