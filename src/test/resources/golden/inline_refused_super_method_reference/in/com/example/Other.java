package com.example;

public class Other extends Base {
  String go(Sub x) {
    return x.up().get();
  }
}
