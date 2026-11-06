package shop.export;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExportTest {
  @Test
  void csv() {
    assertEquals(
        "id,customer,city,total\nA1,Ann,Springfield,3.60\nB2,Bob,Shelbyville,47.04\n"
            + "D4,Di,Ogdenville,12.00\n",
        Csv.write(Sample.ORDERS));
  }

  @Test
  void json() {
    assertEquals(
        """
        [
          {
            "id": "A1",
            "customer": "Ann",
            "city": "Springfield",
            "total": "3.60"
          },
          {
            "id": "B2",
            "customer": "Bob",
            "city": "Shelbyville",
            "total": "47.04"
          },
          {
            "id": "D4",
            "customer": "Di",
            "city": "Ogdenville",
            "total": "12.00"
          }
        ]
        """,
        Json.write(Sample.ORDERS));
  }

  @Test
  void markdown() {
    assertEquals(
        "| Order | Customer | Total |\n|---|---|---|\n| A1 | Ann | $3.60 |\n| B2 | Bob | $47.04 |\n"
            + "| D4 | Di | $12.00 |\n\nTotal: $62.64\n",
        Markdown.write(Sample.ORDERS));
  }
}
