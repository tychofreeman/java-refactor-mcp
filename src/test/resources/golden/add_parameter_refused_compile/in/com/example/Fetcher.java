package com.example;

public class Fetcher {
  String fetch(String url) {
    int retries = 2;
    return url + retries;
  }

  String home() {
    return fetch("/");
  }
}
