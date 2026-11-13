package com.example;

class Pricing {
  static class Order {
    String customer;
    int amount;
  }

  record Cart(Order order) {}

  String priceOf(Order order) {
    String customer = order.customer;
    if (order.amount == 0) {
      return customer;
    }
    return customer + ": " + order.amount;
  }

  String both(Order order, Cart cart) {
    return priceOf(order) + priceOf(cart.order());
  }
}
