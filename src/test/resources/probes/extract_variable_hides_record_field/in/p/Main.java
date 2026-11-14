package p;
public record Main(int base) {
  int add(int a) {
    int b = a * 3;
    return b + base;
  }
  public static void main(String[] args) {
    System.out.println(new Main(5).add(2));
  }
}
