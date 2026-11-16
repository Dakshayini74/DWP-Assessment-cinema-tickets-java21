package uk.gov.dwp.uc.pairtest;

import java.util.Objects;
import thirdparty.paymentgateway.TicketPaymentService;
import thirdparty.paymentgateway.TicketPaymentServiceImpl;
import thirdparty.seatbooking.SeatReservationService;
import thirdparty.seatbooking.SeatReservationServiceImpl;
import uk.gov.dwp.uc.pairtest.domain.TicketTypeRequest;
import uk.gov.dwp.uc.pairtest.exception.InvalidPurchaseException;

/**
 * Coordinates one cinema ticket purchase. Every invocation constructs its own
 * basket; no ticket totals are stored on the service between purchases.
 */
public class TicketServiceImpl implements TicketService {
    private final TicketPaymentService paymentGateway;
    private final SeatReservationService seatBookingGateway;

    public TicketServiceImpl() {
        this(new TicketPaymentServiceImpl(), new SeatReservationServiceImpl());
    }

    /** Allows integrations to be replaced with test doubles. */
    public TicketServiceImpl(TicketPaymentService paymentGateway,
                             SeatReservationService seatBookingGateway) {
        this.paymentGateway = Objects.requireNonNull(paymentGateway, "paymentGateway");
        this.seatBookingGateway = Objects.requireNonNull(seatBookingGateway, "seatBookingGateway");
    }

    @Override
    public void purchaseTickets(Long accountId, TicketTypeRequest... ticketTypeRequests)
            throws InvalidPurchaseException {
        if (accountId == null || accountId.longValue() <= 0L) {
            throw new InvalidPurchaseException();
        }

        TicketBasket basket = TicketBasket.createFrom(ticketTypeRequests);

        // The complete basket is verified before either external dependency runs.
        paymentGateway.makePayment(accountId, basket.costInPounds());
        seatBookingGateway.reserveSeat(accountId, basket.seatsNeeded());
    }

    /**
     * Short-lived, stateful domain object. It owns its counting and pricing
     * rules instead of exposing counters to the service orchestration layer.
     */
    private static final class TicketBasket {
        private static final int CAPACITY = 25;
        private static final int ADULT_FARE = 25;
        private static final int CHILD_FARE = 15;

        private int adults;
        private int children;
        private int ticketCount;

        private TicketBasket() { }

        static TicketBasket createFrom(TicketTypeRequest[] requests) {
            if (requests == null || requests.length == 0) {
                throw new InvalidPurchaseException();
            }

            TicketBasket basket = new TicketBasket();
            for (TicketTypeRequest request : requests) {
                basket.add(request);
            }
            basket.checkAdultIsPresent();
            return basket;
        }

        private void add(TicketTypeRequest request) {
            if (request == null || request.getTicketType() == null) {
                throw new InvalidPurchaseException();
            }

            int requestedCount = request.getNoOfTickets();
            // Subtraction prevents overflow even for Integer.MAX_VALUE input.
            if (requestedCount <= 0 || requestedCount > CAPACITY - ticketCount) {
                throw new InvalidPurchaseException();
            }

            switch (request.getTicketType()) {
                case ADULT -> adults += requestedCount;
                case CHILD -> children += requestedCount;
                case INFANT -> { /* Infants are free and do not occupy seats. */ }
            }
            ticketCount += requestedCount;
        }

        private void checkAdultIsPresent() {
            if (adults == 0) {
                throw new InvalidPurchaseException();
            }
        }

        int costInPounds() {
            return adults * ADULT_FARE + children * CHILD_FARE;
        }

        int seatsNeeded() {
            return adults + children;
        }
    }
}
