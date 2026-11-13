package com.example;

class Pay {
  static final int FULL = 40;
  final int rate;

  Pay(int rate) {
    this.rate = rate;
  }

  int due(int hours) {
    final int extra = (hours - Pay.FULL) * rate;
    return extra > 0 ? hours * rate + extra : hours * rate;
  }

  int week(int worked) {
    return due(worked);
  }
}
