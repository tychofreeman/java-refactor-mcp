package com.example;

class Price {
  int total(int qty, int unit) {
    return qty * unit + qty / 10;
  }

  int next() {
    return 1;
  }

  int use(int n) {
    return total(n, 5) + total(next(), 3);
  }
}
