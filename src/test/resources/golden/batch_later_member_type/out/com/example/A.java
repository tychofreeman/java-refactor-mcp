package com.example;

class A {
  Query parse(String text) {
    return new Query(text);
  }

  record Query(String text) {}
}
