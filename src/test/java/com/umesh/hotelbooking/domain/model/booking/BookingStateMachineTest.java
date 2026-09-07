package com.umesh.hotelbooking.domain.model.booking;

import com.umesh.hotelbooking.domain.exception.InvalidStateTransitionException;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BookingStateMachineTest {

    private final BookingStateMachine fsm = new BookingStateMachine();

    /**
     * Mirrors the table in spec §2.7 exactly, independent of the production implementation,
     * so this test can genuinely catch a mistranscribed table rather than just re-asserting
     * whatever the production code happens to contain.
     */
    private static Map<BookingState, Set<BookingState>> expectedTable() {
        Map<BookingState, Set<BookingState>> table = new EnumMap<>(BookingState.class);
        table.put(BookingState.CREATED, EnumSet.of(BookingState.PENDING_PAYMENT, BookingState.EXPIRED));
        table.put(BookingState.PENDING_PAYMENT, EnumSet.of(
                BookingState.CONFIRMED, BookingState.PAYMENT_FAILED,
                BookingState.PAYMENT_UNKNOWN, BookingState.EXPIRED));
        table.put(BookingState.PAYMENT_UNKNOWN, EnumSet.of(
                BookingState.CONFIRMED, BookingState.PAYMENT_FAILED,
                BookingState.MANUAL_REVIEW, BookingState.REVERSED));
        table.put(BookingState.MANUAL_REVIEW, EnumSet.of(
                BookingState.CONFIRMED, BookingState.PAYMENT_FAILED, BookingState.REVERSED));
        table.put(BookingState.CONFIRMED, EnumSet.of(BookingState.CANCELLED, BookingState.COMPLETED));
        table.put(BookingState.PAYMENT_FAILED, EnumSet.of(BookingState.EXPIRED));
        table.put(BookingState.CANCELLED, EnumSet.noneOf(BookingState.class));
        table.put(BookingState.COMPLETED, EnumSet.noneOf(BookingState.class));
        table.put(BookingState.EXPIRED, EnumSet.noneOf(BookingState.class));
        table.put(BookingState.REVERSED, EnumSet.noneOf(BookingState.class));
        return table;
    }

    @Test
    void everyAllowedTransitionInTheTableReturnsTrue() {
        expectedTable().forEach((from, tos) ->
                tos.forEach(to -> assertThat(fsm.canTransition(from, to))
                        .as("%s -> %s should be allowed", from, to)
                        .isTrue()));
    }

    @Test
    void everyDisallowedPairThrowsAcrossTheFullCartesianProduct() {
        Map<BookingState, Set<BookingState>> expected = expectedTable();

        for (BookingState from : BookingState.values()) {
            for (BookingState to : BookingState.values()) {
                boolean shouldBeAllowed = from == to || expected.get(from).contains(to);

                if (shouldBeAllowed) {
                    assertThat(fsm.canTransition(from, to))
                            .as("%s -> %s should be allowed", from, to)
                            .isTrue();
                } else {
                    assertThat(fsm.canTransition(from, to))
                            .as("%s -> %s should be rejected", from, to)
                            .isFalse();
                    assertThatThrownBy(() -> fsm.assertCanTransition(from, to))
                            .as("%s -> %s should throw", from, to)
                            .isInstanceOf(InvalidStateTransitionException.class);
                }
            }
        }
    }

    @Test
    void sameStateTransitionIsPermittedForAllStates() {
        for (BookingState state : BookingState.values()) {
            assertThat(fsm.canTransition(state, state))
                    .as("%s -> %s (same state) should be a permitted no-op", state, state)
                    .isTrue();
        }
    }

    @Test
    void terminalStatesHaveEmptyAllowedSetAndRejectEverythingExceptThemselves() {
        Set<BookingState> terminalStates = EnumSet.of(
                BookingState.CANCELLED, BookingState.COMPLETED,
                BookingState.EXPIRED, BookingState.REVERSED);

        for (BookingState terminal : terminalStates) {
            assertThat(fsm.isTerminal(terminal)).isTrue();
            assertThat(fsm.allowedFrom(terminal)).isEmpty();

            for (BookingState other : BookingState.values()) {
                if (other == terminal) {
                    assertThat(fsm.canTransition(terminal, other)).isTrue();
                } else {
                    assertThat(fsm.canTransition(terminal, other))
                            .as("terminal state %s should reject %s", terminal, other)
                            .isFalse();
                }
            }
        }
    }

    @Test
    void everyEnumConstantHasATableEntry() {
        // Guards against a future state being added to the enum and silently defaulting to
        // permissive because allowedFrom/isTerminal would otherwise NPE or misbehave.
        for (BookingState state : BookingState.values()) {
            assertThat(fsm.allowedFrom(state)).isNotNull();
        }
    }
}
