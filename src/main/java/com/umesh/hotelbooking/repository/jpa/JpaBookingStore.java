package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.repository.BookingStore;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** JPA adapter for {@link BookingStore}. */
class JpaBookingStore implements BookingStore {

    private final JpaBookingRepository repository;

    JpaBookingStore(JpaBookingRepository repository) {
        this.repository = repository;
    }

    @Override
    public Booking save(Booking booking) {
        return repository.save(booking);
    }

    @Override
    public Booking saveAndFlush(Booking booking) {
        return repository.saveAndFlush(booking);
    }

    @Override
    public Optional<Booking> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<Booking> findByBookingUid(String bookingUid) {
        return repository.findByBookingUid(bookingUid);
    }

    @Override
    public List<Booking> findByStateInAndHoldExpiresAtBefore(Collection<BookingState> states, Instant cutoff) {
        return repository.findByStateInAndHoldExpiresAtBefore(states, cutoff);
    }

    @Override
    public List<Booking> findByStateAndCheckOutLessThanEqual(BookingState state, LocalDate cutoff) {
        return repository.findByStateAndCheckOutLessThanEqual(state, cutoff);
    }

    @Override
    public void deleteAll() {
        repository.deleteAll();
    }
}
