package com.example;

class Old {
  int kept() {
    return 1;
  }

  int legacy() {
    return helper() + 1;
  }

  private int helper() {
    return 2;
  }
}
