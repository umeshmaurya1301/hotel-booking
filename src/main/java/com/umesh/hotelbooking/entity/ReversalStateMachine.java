package com.umesh.hotelbooking.entity;

import com.umesh.hotelbooking.exception.InvalidStateTransitionException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Total transition table for {@link ReversalState}. */
public final class ReversalStateMachine {

    private static final Map<ReversalState, Set<ReversalState>> ALLOWED = buildTable();

    private ReversalStateMachine() {
    }

    private static Map<ReversalState, Set<ReversalState>> buildTable() {
        Map<ReversalState, Set<ReversalState>> table = new EnumMap<>(ReversalState.class);
        table.put(ReversalState.INITIATED, EnumSet.of(ReversalState.COMPLETED, ReversalState.FAILED));
        table.put(ReversalState.COMPLETED, EnumSet.noneOf(ReversalState.class));
        table.put(ReversalState.FAILED, EnumSet.noneOf(ReversalState.class));
        return Map.copyOf(table);
    }

    public static boolean canTransition(ReversalState from, ReversalState to) {
        return from == to || ALLOWED.get(from).contains(to);
    }

    public static void assertCanTransition(ReversalState from, ReversalState to) {
        if (!canTransition(from, to)) {
            throw new InvalidStateTransitionException(from.name(), to.name());
        }
    }
}
