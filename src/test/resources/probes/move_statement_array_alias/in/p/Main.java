package p;
public class Main {
  public static void main(String[] args) {
    int[] a = new int[1];
    int[] b = a;
    a[0] = 1;
    int x = b[0];
    System.out.println(x);
  }
}
