package com.example;

class Price {
  int total(int qty, int unit) {
    return qty * unit + qty / 10;
  }

  int use(int n) {
    return total(n, 5) + total(n + 1, 3);
  }
}
