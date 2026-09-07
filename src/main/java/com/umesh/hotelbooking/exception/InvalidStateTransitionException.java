package com.umesh.hotelbooking.exception;

/**
 * Thrown when a state machine is asked to move an entity between two states that are not
 * connected in its transition table.
 */
public final class InvalidStateTransitionException extends DomainException {

    private static final long serialVersionUID = 1L;

    public InvalidStateTransitionException(String fromState, String toState) {
        super("INVALID_STATE_TRANSITION", "Cannot transition from " + fromState + " to " + toState);
    }
}
