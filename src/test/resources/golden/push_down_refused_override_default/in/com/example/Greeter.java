package com.example;

public interface Greeter {
  default String hello() {
    return "default";
  }
}
