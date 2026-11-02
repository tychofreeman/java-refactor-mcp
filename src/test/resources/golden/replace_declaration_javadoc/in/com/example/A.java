package com.example;

public class A {
  int a() {
    return b();
  }

  /** B, documented. */
  int b() {
    return 1;
  }
}
