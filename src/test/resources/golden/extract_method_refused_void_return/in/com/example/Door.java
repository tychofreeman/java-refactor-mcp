package com.example;

class Door {
  private int opened;

  void open(boolean ok) {
    if (!ok) {
      return;
    }
    opened++;
  }
}
