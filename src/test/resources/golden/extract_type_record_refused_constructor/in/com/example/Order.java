package com.example;

record Order(String id, String street, String city) {
  Order {
    if (id.isEmpty()) {
      throw new IllegalArgumentException("no id");
    }
  }
}
