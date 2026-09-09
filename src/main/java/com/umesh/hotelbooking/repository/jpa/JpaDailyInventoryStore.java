package com.umesh.hotelbooking.repository.jpa;

import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.repository.DailyInventoryStore;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for {@link DailyInventoryStore}.
 *
 * <p>{@link #reserveUnits} and {@link #releaseUnits} forward the row count unchanged. That is
 * the whole adapter, and it is deliberate: the port defines 0 as "not enough availability", the
 * conditional {@code UPDATE} produces exactly that, and any translation here — a boolean, a
 * thrown exception, a re-read to "confirm" — would put a second decision point between the
 * database's answer and the caller's. There is one place that decides whether a room-night was
 * taken, and it is the statement itself.
 */
class JpaDailyInventoryStore implements DailyInventoryStore {

    private final JpaDailyInventoryRepository repository;

    JpaDailyInventoryStore(JpaDailyInventoryRepository repository) {
        this.repository = repository;
    }

    @Override
    public DailyInventory save(DailyInventory inventory) {
        return repository.save(inventory);
    }

    @Override
    public List<DailyInventory> saveAll(Iterable<DailyInventory> rows) {
        return repository.saveAll(rows);
    }

    @Override
    public DailyInventory saveAndFlush(DailyInventory inventory) {
        return repository.saveAndFlush(inventory);
    }

    @Override
    public Optional<DailyInventory> findByRoomTypeIdAndStayDate(Long roomTypeId, LocalDate stayDate) {
        return repository.findByRoomTypeIdAndStayDate(roomTypeId, stayDate);
    }

    @Override
    public List<DailyInventory> findByRoomTypeIdAndStayDateBetween(Long roomTypeId, LocalDate from, LocalDate to) {
        return repository.findByRoomTypeIdAndStayDateBetween(roomTypeId, from, to);
    }

    @Override
    public List<DailyInventory> findByRoomTypeIdInAndStayDateBetween(Collection<Long> roomTypeIds,
                                                                     LocalDate from,
                                                                     LocalDate to) {
        return repository.findByRoomTypeIdInAndStayDateBetween(roomTypeIds, from, to);
    }

    @Override
    public long countByRoomTypeId(Long roomTypeId) {
        return repository.countByRoomTypeId(roomTypeId);
    }

    @Override
    public int reserveUnits(Long roomTypeId, LocalDate stayDate, int units) {
        return repository.reserveUnits(roomTypeId, stayDate, units);
    }

    @Override
    public int releaseUnits(Long roomTypeId, LocalDate stayDate, int units) {
        return repository.releaseUnits(roomTypeId, stayDate, units);
    }
}
