package p;

public class Main {
  static int calls;

  static String shortForm(String text) {
    calls++;
    String cut = text.length() > 20 ? text.substring(0, 17) : text;
    return cut + "..." + 'x' + 1.5;
  }

  static String tinyForm(String text) {
    calls++;
    String cut = text.length() > 8 ? text.substring(0, 7) : text;
    return cut + "~" + 'y' + 2.5;
  }

  public static void main(String[] args) {
    for (String s : new String[] {"short", "a rather long piece of text"}) {
      System.out.println(shortForm(s) + " " + tinyForm(s));
    }
    System.out.println(calls);
  }
}
