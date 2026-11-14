package p;
public class Main {
  static int next(int a) {
    return (a)++;
  }
  public static void main(String[] args) {
    int x = 1;
    int y = next(x);
    System.out.println(x + " " + y);
  }
}
