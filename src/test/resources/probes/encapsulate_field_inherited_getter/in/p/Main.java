package p;
public class Main {
  public static void main(String[] args) {
    Base b = new Person();
    System.out.println(b.getName());
    System.out.println(((Person) b).name);
  }
}
class Base {
  public String getName() { return "base"; }
}
class Person extends Base {
  String name = "person";
}
