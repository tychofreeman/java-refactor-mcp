package com.example;

public class Tick {
  Runnable make(StringBuilder out) {
    Runnable r = () -> out.append("x");
    return r;
  }
}
