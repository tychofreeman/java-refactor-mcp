package com.example;

public class Signatures {
  record Call(String name) {
    enum Kind {
      A
    }
  }
}
