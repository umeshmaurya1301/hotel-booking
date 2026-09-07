package com.umesh.hotelbooking.entity;

import com.umesh.hotelbooking.exception.InvalidStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for which {@link BookingState} transitions are legal, expressed
 * as an explicit total table rather than scattered conditionals. "Total" means every enum
 * constant has an entry — including terminal states, which map to an empty set — so that a
 * state added to the enum in the future without a corresponding table entry fails loudly
 * instead of silently defaulting to permissive.
 *
 * <p>Stateless, so its methods are static rather than requiring an instance.
 */
public final class BookingStateMachine {

    private static final Map<BookingState, Set<BookingState>> ALLOWED = buildTable();

    private BookingStateMachine() {
    }

    private static Map<BookingState, Set<BookingState>> buildTable() {
        Map<BookingState, Set<BookingState>> table = new EnumMap<>(BookingState.class);
        table.put(BookingState.CREATED,
                EnumSet.of(BookingState.PENDING_PAYMENT, BookingState.EXPIRED));
        table.put(BookingState.PENDING_PAYMENT,
                EnumSet.of(BookingState.CONFIRMED, BookingState.PAYMENT_FAILED,
                        BookingState.PAYMENT_UNKNOWN, BookingState.EXPIRED));
        table.put(BookingState.PAYMENT_UNKNOWN,
                EnumSet.of(BookingState.CONFIRMED, BookingState.PAYMENT_FAILED,
                        BookingState.MANUAL_REVIEW, BookingState.REVERSED));
        table.put(BookingState.MANUAL_REVIEW,
                EnumSet.of(BookingState.CONFIRMED, BookingState.PAYMENT_FAILED, BookingState.REVERSED));
        table.put(BookingState.CONFIRMED,
                EnumSet.of(BookingState.CANCELLED, BookingState.COMPLETED));
        table.put(BookingState.PAYMENT_FAILED,
                EnumSet.of(BookingState.EXPIRED));
        table.put(BookingState.CANCELLED, EnumSet.noneOf(BookingState.class));
        table.put(BookingState.COMPLETED, EnumSet.noneOf(BookingState.class));
        table.put(BookingState.EXPIRED, EnumSet.noneOf(BookingState.class));
        table.put(BookingState.REVERSED, EnumSet.noneOf(BookingState.class));
        return Map.copyOf(table);
    }

    /**
     * True for any transition listed in the table, and — as a deliberate special case — for
     * every same-state transition, including from a terminal state to itself. Payment
     * gateways retry webhooks; a duplicate {@code PAYMENT_SUCCESS} re-applying
     * {@code CONFIRMED -> CONFIRMED} must be a silent no-op, not a thrown exception.
     */
    public static boolean canTransition(BookingState from, BookingState to) {
        if (from == to) {
            return true;
        }
        return ALLOWED.get(from).contains(to);
    }

    /**
     * @throws InvalidStateTransitionException naming both states, if the transition is not
     *     permitted by {@link #canTransition}
     */
    public static void assertCanTransition(BookingState from, BookingState to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }

    public static boolean isTerminal(BookingState state) {
        return ALLOWED.get(state).isEmpty();
    }

    /** The set of other states reachable from {@code state}; does not include {@code state} itself. */
    public static Set<BookingState> allowedFrom(BookingState state) {
        return ALLOWED.get(state);
    }
}
