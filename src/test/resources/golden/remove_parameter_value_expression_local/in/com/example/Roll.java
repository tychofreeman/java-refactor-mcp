package com.example;

class Roll extends Pay {
  Roll() {
    super(12);
  }

  int twoWeeks(int[] worked) {
    return due(worked[0], (worked[0] - FULL) * rate) + week(worked[1]);
  }
}
