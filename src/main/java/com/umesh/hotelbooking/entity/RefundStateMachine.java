package com.umesh.hotelbooking.entity;

import com.umesh.hotelbooking.exception.InvalidStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Total transition table for {@link RefundState}, in the same shape as the other state machines. */
public final class RefundStateMachine {

    private static final Map<RefundState, Set<RefundState>> ALLOWED = buildTable();

    private RefundStateMachine() {
    }

    private static Map<RefundState, Set<RefundState>> buildTable() {
        Map<RefundState, Set<RefundState>> table = new EnumMap<>(RefundState.class);
        table.put(RefundState.REQUESTED, EnumSet.of(RefundState.PROCESSING, RefundState.FAILED));
        table.put(RefundState.PROCESSING, EnumSet.of(RefundState.COMPLETED, RefundState.FAILED));
        table.put(RefundState.COMPLETED, EnumSet.noneOf(RefundState.class));
        table.put(RefundState.FAILED, EnumSet.noneOf(RefundState.class));
        return Map.copyOf(table);
    }

    public static boolean canTransition(RefundState from, RefundState to) {
        return from == to || ALLOWED.get(from).contains(to);
    }

    public static void assertCanTransition(RefundState from, RefundState to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }
}
