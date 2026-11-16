# Cinema Tickets — Java 21

A compact ticket-booking service based on the supplied DWP Java starter project.
It uses a short-lived **ticket basket domain object** for validation, aggregation,
and pricing, plus a service class for coordinating the payment and seat-booking
interfaces.

## Design choices

- **TicketServiceImpl** validates the account and coordinates payment then seat
  reservation. The supplied interface is unchanged.
- **TicketBasket** (a private nested class within `TicketServiceImpl`) tracks an
  individual purchase. It owns ticket counts, fares and seat rules. A new basket
  is built for each request; there is no shared purchase state.
- Ticket categories are processed with an imperative `for` loop and `switch`.
- Testing uses small **recording gateway fakes** rather than a mocking library.
- The original starter `TicketService`, `TicketTypeRequest`,
  `InvalidPurchaseException` and all `thirdparty` Java files are preserved
  byte-for-byte.

## Business rules

| Category | Cost | Seat allocation |
|---|---:|---:|
| ADULT | £25 | 1 |
| CHILD | £15 | 1 |
| INFANT | £0 | 0 |

A request must have a positive account ID, 1–25 tickets in total, positive
quantities, known categories and at least one adult ticket. Repeat occurrences
of a category are added together. There is **no invented 1-infant-per-adult
limit**. All input checks finish before either external service is called.

## Run the automated tests

Use **JDK 21** and Maven (IntelliJ IDEA can use its bundled Maven):

```bash
mvn clean test
```

The JUnit 5 suite checks ticket totals, exceptions, maximum limits, repeated
categories, external call ordering, payment failure and service reuse.

## Code example

```java
TicketService booking = new TicketServiceImpl();
booking.purchaseTickets(42L,
    new TicketTypeRequest(TicketTypeRequest.Type.ADULT, 2),
    new TicketTypeRequest(TicketTypeRequest.Type.CHILD, 1),
    new TicketTypeRequest(TicketTypeRequest.Type.INFANT, 1));
// Total: £65; seats to reserve: 3.
```

The included third-party implementations are provided stubs, not real payment
or cinema reservation integrations. This project is an assessment-sized example,
not a production checkout system.
