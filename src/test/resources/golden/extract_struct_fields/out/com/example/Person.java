package com.example;

public class Person {
  private final Address home = new Address();
  private String name;

  Person(String name) {
    this.name = name;
  }

  String address() {
    return home.street + ", " + home.city;
  }

  void move(String street) {
    this.home.street = street;
  }
}
