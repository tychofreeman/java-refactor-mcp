package p;
interface Limits {
  int MAX = 10;
}
public class Main implements Limits {
  int cap(int a) {
    int b = a + 1;
    return Math.min(b, MAX);
  }
  public static void main(String[] args) {
    System.out.println(new Main().cap(20));
  }
}
