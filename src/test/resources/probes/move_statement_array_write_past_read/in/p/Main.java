package p;
public class Main {
  public static void main(String[] args) {
    int[] a = new int[1];
    int[] b = a;
    int x = b[0];
    a[0] = 1;
    System.out.println(x);
  }
}
