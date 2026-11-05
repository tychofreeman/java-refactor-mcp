package com.example;

interface Base {
  default String label() {
    return "base";
  }
}
