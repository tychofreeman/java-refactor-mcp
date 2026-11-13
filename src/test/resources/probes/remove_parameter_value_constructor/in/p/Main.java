package p;
public class Main {
  final String text;
  Main(String text) { this.text = text; }
  Main(int a, int b, int sum) {
    this("" + a);
    System.out.println(text + " " + b + " " + sum);
  }
  public static void main(String[] args) {
    new Main(1, 2, 1 + 2);
    int k = 4;
    new Main(k, k * 2, k + k * 2);
  }
}
