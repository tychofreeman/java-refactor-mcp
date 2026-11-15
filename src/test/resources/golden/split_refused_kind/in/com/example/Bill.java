package com.example;

class Bill {
  int rows;

  int price(int days, int rate) {
    int litres = rows * 2;
    int extra = days > 3 ? 1 : 0;
    int total = litres * days + extra;
    return total * rate;
  }
}
