package com.example;

class Use {
  int four() {
    return Num.twice(2);
  }

  String abab() {
    return Num.twice("ab");
  }

  int six(int n) {
    return Num.twice(n) + Num.twice(1);
  }
}
