package p;
public class Main {
  public static void main(String[] args) {
    int i = 0;
    while (i < 9) {
      i++;
      if (i % 3 == 0) continue;
      System.out.print(i + " ");
      i++;
    }
    System.out.println();
  }
}