package com.example;

record Line(int state) {
  boolean open() {
    return state == Order.OPEN;
  }
}
