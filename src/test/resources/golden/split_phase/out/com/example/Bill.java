package com.example;

class Bill {
  int rows;

  int price(int days, int rate) {
    int litres = rows * 2;
    int extra = days > 3 ? 1 : 0;
    return charge(new Usage(litres, extra), days, rate);
  }

  private int charge(Usage usage, int days, int rate) {
    int total = usage.litres() * days + usage.extra();
    return total * rate;
  }
}
