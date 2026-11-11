package p;
public class Main {
  public static void main(String[] args) {
    for (int n = 0; n < 3; n++) {
      int i = 0;
      while (i < n) {
        System.out.print(n + ":" + i + " ");
        i++;
      }
    }
    System.out.println();
  }
}