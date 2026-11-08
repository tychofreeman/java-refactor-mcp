package com.example;

public class Cart {
  int count;
  int price;

  int total() {
    return count * price;
  }

  int withShipping() {
    return count * price + 5;
  }

  Runnable printer() {
    return new Runnable() {
      @Override
      public void run() {
        System.out.println(count * price);
      }
    };
  }
}

class BigCart extends Cart {
  int twice() {
    int t = count * price;
    return t * 2;
  }
}
