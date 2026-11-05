package p;
public class Main {
  static String line(String room, String what) { return String.format("%s: %s!", room, what); }
  public static void main(String[] args) {
    System.out.println(String.format("%s: a.*(b)\\ \"q\"!", "r"));
    System.out.println(String.format("%s: 100%%!", "r"));
  }
}
