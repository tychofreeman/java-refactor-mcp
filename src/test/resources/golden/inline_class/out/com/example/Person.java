package com.example;

public class Person {
  String street = "";
  String city = "";
  private String name;

  Person(String name) {
    this.name = name;
  }

  String address() {
    return street + ", " + city;
  }

  void move(String street) {
    this.street = street;
  }
}
