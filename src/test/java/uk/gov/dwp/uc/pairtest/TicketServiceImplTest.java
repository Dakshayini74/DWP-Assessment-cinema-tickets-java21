package uk.gov.dwp.uc.pairtest;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.seatbooking.SeatReservationService;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest.Type;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

import static org.junit.jupiter.api.Assertions.*;

/** Behavioural tests use recording gateways, not the supplied third-party stubs. */
class TicketServiceImplTest {
    private RecordingPayment payment;
    private RecordingSeats seating;
    private List<String> chronology;
    private TicketService service;

    @BeforeEach
    void createPurchaseService() {
        chronology = new ArrayList<>();
        payment = new RecordingPayment(chronology);
        seating = new RecordingSeats(chronology);
        service = new TicketServiceImpl(payment, seating);
    }

    @Test
    void singleAdultTicketCostsTwentyFivePoundsAndOccupiesOneSeat() {
        service.purchaseTickets(12L, ticket(Type.ADULT, 1));
        assertBooking(12L, 25, 1);
    }

    @Test
    void childrenHaveReducedFareAndInfantsDoNotRequireSeats() {
        service.purchaseTickets(87L, ticket(Type.ADULT, 2), ticket(Type.CHILD, 3),
                ticket(Type.INFANT, 1));
        assertBooking(87L, 95, 5);
    }

    @Test
    void multipleInfantsCanAccompanyAnAdult() {
        service.purchaseTickets(5L, ticket(Type.ADULT, 1), ticket(Type.INFANT, 2));
        assertBooking(5L, 25, 1);
    }

    @Test
    void repeatedTicketCategoriesAreAccumulated() {
        service.purchaseTickets(9L, ticket(Type.CHILD, 1), ticket(Type.ADULT, 1),
                ticket(Type.INFANT, 1), ticket(Type.ADULT, 2), ticket(Type.CHILD, 2));
        assertBooking(9L, 120, 6);
    }

    @Test
    void exactlyTwentyFiveTicketsAreAllowed() {
        service.purchaseTickets(3L, ticket(Type.ADULT, 13), ticket(Type.INFANT, 12));
        assertBooking(3L, 325, 13);
    }

    @Test
    void overTwentyFiveTicketsAreRejectedRegardlessOfCategory() {
        rejectsWithoutCallingPartners(3L, ticket(Type.ADULT, 1), ticket(Type.CHILD, 25));
        rejectsWithoutCallingPartners(3L, ticket(Type.ADULT, 13), ticket(Type.INFANT, 13));
    }

    @Test
    void overflowSizedRequestIsRejected() {
        rejectsWithoutCallingPartners(3L, ticket(Type.ADULT, Integer.MAX_VALUE));
        rejectsWithoutCallingPartners(3L, ticket(Type.ADULT, 1), ticket(Type.CHILD, Integer.MAX_VALUE));
    }

    @Test
    void invalidAccountIdIsRejected() {
        rejectsWithoutCallingPartners(null, ticket(Type.ADULT, 1));
        rejectsWithoutCallingPartners(0L, ticket(Type.ADULT, 1));
        rejectsWithoutCallingPartners(-1L, ticket(Type.ADULT, 1));
    }

    @Test
    void ticketRequestsMustBePresent() {
        rejectsWithoutCallingPartners(1L, (TicketTypeRequest[]) null);
        rejectsWithoutCallingPartners(1L);
        rejectsWithoutCallingPartners(1L, (TicketTypeRequest) null);
        rejectsWithoutCallingPartners(1L, ticket(Type.ADULT, 1), null);
    }

    @Test
    void everyRequestNeedsAValidTypeAndPositiveQuantity() {
        rejectsWithoutCallingPartners(1L, ticket(null, 1));
        rejectsWithoutCallingPartners(1L, ticket(Type.ADULT, 0));
        rejectsWithoutCallingPartners(1L, ticket(Type.ADULT, -3));
        rejectsWithoutCallingPartners(1L, ticket(Type.ADULT, Integer.MIN_VALUE));
    }

    @Test
    void childOrInfantOnlyPurchasesAreRejected() {
        rejectsWithoutCallingPartners(1L, ticket(Type.CHILD, 2));
        rejectsWithoutCallingPartners(1L, ticket(Type.INFANT, 2));
        rejectsWithoutCallingPartners(1L, ticket(Type.CHILD, 1), ticket(Type.INFANT, 1));
    }

    @Test
    void paymentMustCompleteBeforeReservationStarts() {
        payment.throwOnCharge = true;
        assertThrows(IllegalStateException.class,
                () -> service.purchaseTickets(1L, ticket(Type.ADULT, 2)));
        assertEquals(List.of("payment"), chronology);
        assertEquals(0, seating.callCount);
    }

    @Test
    void serviceDoesNotReuseBasketTotalsAcrossCalls() {
        service.purchaseTickets(4L, ticket(Type.ADULT, 1));
        service.purchaseTickets(4L, ticket(Type.ADULT, 2), ticket(Type.CHILD, 1));
        assertEquals(List.of("payment", "reservation", "payment", "reservation"), chronology);
        assertEquals(List.of(25, 65), payment.amounts);
        assertEquals(List.of(1, 3), seating.seatCounts);
    }

    @Test
    void constructorRejectsMissingDependencies() {
        assertThrows(NullPointerException.class,
                () -> new TicketServiceImpl(null, seating));
        assertThrows(NullPointerException.class,
                () -> new TicketServiceImpl(payment, null));
    }

    private void assertBooking(long expectedAccount, int expectedCost, int expectedSeats) {
        assertEquals(List.of("payment", "reservation"), chronology);
        assertEquals(List.of(expectedAccount), payment.accounts);
        assertEquals(List.of(expectedCost), payment.amounts);
        assertEquals(List.of(expectedAccount), seating.accounts);
        assertEquals(List.of(expectedSeats), seating.seatCounts);
    }

    private void rejectsWithoutCallingPartners(Long account, TicketTypeRequest... requests) {
        assertThrows(InvalidPurchaseException.class,
                () -> service.purchaseTickets(account, requests));
        assertTrue(chronology.isEmpty(), "Invalid requests must not invoke payment or booking");
    }

    private static TicketTypeRequest ticket(Type category, int quantity) {
        return new TicketTypeRequest(category, quantity);
    }

    private static final class RecordingPayment implements TicketPaymentService {
        private final List<String> chronology;
        private final List<Long> accounts = new ArrayList<>();
        private final List<Integer> amounts = new ArrayList<>();
        private boolean throwOnCharge;

        private RecordingPayment(List<String> chronology) {
            this.chronology = chronology;
        }

        @Override
        public void makePayment(long accountId, int amount) {
            chronology.add("payment");
            if (throwOnCharge) {
                throw new IllegalStateException("Simulated payment failure");
            }
            accounts.add(accountId);
            amounts.add(amount);
        }
    }

    private static final class RecordingSeats implements SeatReservationService {
        private final List<String> chronology;
        private final List<Long> accounts = new ArrayList<>();
        private final List<Integer> seatCounts = new ArrayList<>();
        private int callCount;

        private RecordingSeats(List<String> chronology) {
            this.chronology = chronology;
        }

        @Override
        public void reserveSeat(long accountId, int seats) {
            chronology.add("reservation");
            callCount++;
            accounts.add(accountId);
            seatCounts.add(seats);
        }
    }
}
