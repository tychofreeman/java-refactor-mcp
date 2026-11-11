package p;
public class Main {
  static int checks = 0;
  static boolean more(int i) { checks++; return i < 5; }
  public static void main(String[] args) {
    int i = 10;
    do {
      i++;
      if (i == 13) break;
      System.out.print(i + " ");
    } while (more(i) || i < 12);
    System.out.println(i + " " + checks);
  }
}
