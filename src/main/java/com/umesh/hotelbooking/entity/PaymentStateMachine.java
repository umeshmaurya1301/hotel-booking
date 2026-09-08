package com.umesh.hotelbooking.entity;

import com.umesh.hotelbooking.exception.InvalidStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The transition table for {@link PaymentState} (design doc 3.5, 7.6.3), mirroring
 * {@link BookingStateMachine}'s shape: explicit total table, same-state is a permitted no-op,
 * static because the machine is stateless.
 *
 * <p>{@code MANUAL_REVIEW} is deliberately not terminal — it is a parking state with a human
 * in the loop, resolved by an admin call rather than automation. Making it terminal would
 * leave the system permanently unable to reach the correct outcome.
 */
public final class PaymentStateMachine {

    private static final Map<PaymentState, Set<PaymentState>> ALLOWED = buildTable();

    private PaymentStateMachine() {
    }

    private static Map<PaymentState, Set<PaymentState>> buildTable() {
        Map<PaymentState, Set<PaymentState>> table = new EnumMap<>(PaymentState.class);
        table.put(PaymentState.INITIATED, EnumSet.of(PaymentState.PROCESSING));
        table.put(PaymentState.PROCESSING,
                EnumSet.of(PaymentState.SETTLED, PaymentState.FAILED, PaymentState.UNKNOWN));
        table.put(PaymentState.UNKNOWN,
                EnumSet.of(PaymentState.SETTLED, PaymentState.FAILED, PaymentState.MANUAL_REVIEW));
        table.put(PaymentState.MANUAL_REVIEW, EnumSet.of(PaymentState.SETTLED, PaymentState.FAILED));
        table.put(PaymentState.SETTLED, EnumSet.noneOf(PaymentState.class));
        table.put(PaymentState.FAILED, EnumSet.noneOf(PaymentState.class));
        return Map.copyOf(table);
    }

    public static boolean canTransition(PaymentState from, PaymentState to) {
        if (from == to) {
            return true;
        }
        return ALLOWED.get(from).contains(to);
    }

    public static void assertCanTransition(PaymentState from, PaymentState to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }

    public static boolean isTerminal(PaymentState state) {
        return ALLOWED.get(state).isEmpty();
    }
}
