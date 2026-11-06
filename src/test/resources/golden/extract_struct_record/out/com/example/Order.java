package com.example;

public record Order(String id, Address address, int total) {
  String label() {
    return id + ": " + address.street() + ", " + address().city();
  }
}
