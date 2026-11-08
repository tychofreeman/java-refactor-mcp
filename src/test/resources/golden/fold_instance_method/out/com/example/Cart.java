package com.example;

public class Cart {
  int count;
  int price;

  int total() {
    return count * price;
  }

  int withShipping() {
    return total() + 5;
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
    int t = total();
    return t * 2;
  }
}
