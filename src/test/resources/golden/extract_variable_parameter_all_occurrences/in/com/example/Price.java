package com.example;

class Price {
  int total(int qty, int unit) {
    int base = qty * unit + qty / 10;
    return base - (qty / 10) * 2 + qty / 100;
  }

  int use(int n) {
    return total(n, 5) + total(n + 1, 3);
  }
}
