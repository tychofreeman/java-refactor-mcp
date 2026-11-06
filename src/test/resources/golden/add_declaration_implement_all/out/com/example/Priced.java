package com.example;

interface Priced {
  int price();

  default String label() {
    return "";
  }
}
