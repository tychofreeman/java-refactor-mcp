package com.example;

public final class Hold {
  private final String member;

  private final int position;

  Hold(String member, int position) {
    this.member = member.strip();
    this.position = position;
  }
}
