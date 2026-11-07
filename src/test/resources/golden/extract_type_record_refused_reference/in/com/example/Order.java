package com.example;

import java.util.List;

record Order(String id, String street, String city) {
  static List<String> streets(List<Order> orders) {
    return orders.stream().map(Order::street).toList();
  }
}
