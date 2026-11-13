package p;

import java.util.Arrays;

public class Main {
  enum Color {
    RED,
    GREEN,
    BLUE {
      @Override
      public String toString() {
        return "blue!";
      }
    }
  }

  public static void main(String[] args) {
    System.out.println(Arrays.toString(Color.values()));
    for (Color c : Color.values()) {
      System.out.println(c + " " + c.name() + " " + c.ordinal() + " " + (Color.valueOf(c.name()) == c));
    }
  }
}
