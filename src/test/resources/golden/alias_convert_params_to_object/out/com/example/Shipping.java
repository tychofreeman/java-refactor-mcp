package com.example;

public class Shipping {
  int cost(Parcel parcel) {
    return parcel.weight() * parcel.width() * parcel.height();
  }

  int total() {
    return cost(new Parcel(2, 3, 4));
  }
}
