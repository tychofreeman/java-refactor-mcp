package com.example;

class Price {
  int total(int qty, int unit, int bonus) {
    return qty * unit + bonus;
  }

  int use(int n) {
    return total(n, 5, n / 10) + total(n + 1, 3, (n + 1) / 10);
  }
}
