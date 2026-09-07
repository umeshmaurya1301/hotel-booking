package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.BookingProperties;
import com.umesh.hotelbooking.dto.BookingResponse;
import com.umesh.hotelbooking.dto.CreateBookingRequest;
import com.umesh.hotelbooking.entity.Booking;
import com.umesh.hotelbooking.entity.BookingLineItem;
import com.umesh.hotelbooking.entity.BookingState;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.Guest;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.event.BookingCreatedEvent;
import com.umesh.hotelbooking.exception.BookingNotFoundException;
import com.umesh.hotelbooking.exception.GuestCapacityExceededException;
import com.umesh.hotelbooking.exception.GuestNotFoundException;
import com.umesh.hotelbooking.exception.InventoryUnavailableException;
import com.umesh.hotelbooking.exception.PropertyNotFoundException;
import com.umesh.hotelbooking.exception.RoomTypeNotFoundException;
import com.umesh.hotelbooking.repository.BookingRepository;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import com.umesh.hotelbooking.repository.GuestRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RoomTypeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Creates one booking inside one transaction.
 *
 * <p>Split from {@link BookingService} on purpose: the retry lives out there, the transaction
 * lives in here. Retrying inside a transaction that has already rolled back would achieve
 * nothing, so the retry has to sit on a different bean's method to be outside this
 * transaction boundary. Two small classes buy a guarantee that annotation ordering would
 * otherwise leave to chance.
 */
@Service
public class BookingCreator {

    private final RoomTypeRepository roomTypeRepository;
    private final PropertyRepository propertyRepository;
    private final GuestRepository guestRepository;
    private final BookingRepository bookingRepository;
    private final DailyInventoryRepository dailyInventoryRepository;
    private final InventoryReservationService reservationService;
    private final BookingProperties bookingProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public BookingCreator(RoomTypeRepository roomTypeRepository,
                          PropertyRepository propertyRepository,
                          GuestRepository guestRepository,
                          BookingRepository bookingRepository,
                          DailyInventoryRepository dailyInventoryRepository,
                          InventoryReservationService reservationService,
                          BookingProperties bookingProperties,
                          ApplicationEventPublisher eventPublisher,
                          Clock clock) {
        this.roomTypeRepository = roomTypeRepository;
        this.propertyRepository = propertyRepository;
        this.guestRepository = guestRepository;
        this.bookingRepository = bookingRepository;
        this.dailyInventoryRepository = dailyInventoryRepository;
        this.reservationService = reservationService;
        this.bookingProperties = bookingProperties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse create(CreateBookingRequest request) {
        RoomType roomType = roomTypeRepository.findByRoomTypeUid(request.roomTypeUid())
                .orElseThrow(() -> new RoomTypeNotFoundException(request.roomTypeUid()));
        Property property = propertyRepository.findById(roomType.getProperty().getId())
                .orElseThrow(() -> new PropertyNotFoundException("for room type " + request.roomTypeUid()));
        Guest guest = resolveGuest(request.guestUid());

        Instant now = clock.instant();
        Booking booking = Booking.builder()
                .guestId(guest.getId())
                .propertyId(property.getId())
                .roomTypeId(roomType.getId())
                .checkIn(request.checkIn())
                .checkOut(request.checkOut())
                .units(request.units())
                .adults(request.adults())
                .children(request.children())
                .currency(property.getCurrency())
                .createdAt(now)
                .holdExpiresAt(now.plus(bookingProperties.holdTtl()))
                .state(BookingState.CREATED)
                .build();

        // Rejects a zero-night stay and an over-long one before anything is reserved.
        booking.validateDateRange();
        List<LocalDate> nights = booking.nights();

        requireCapacity(roomType, request);

        // Prices are read BEFORE reserving. The reservation is a bulk UPDATE that bypasses
        // the persistence context, so any DailyInventory entity read after it would hold a
        // stale booked_units. Reading first also means the snapshot below reflects the price
        // that was on offer when availability was checked.
        Map<LocalDate, DailyInventory> inventoryByNight = loadInventory(roomType, nights, request.roomTypeUid());

        reservationService.reserve(roomType.getId(), request.roomTypeUid(), nights, request.units());

        BigDecimal total = BigDecimal.ZERO;
        for (LocalDate night : nights) {
            BigDecimal pricePerUnit = inventoryByNight.get(night).getPricePerUnit();
            BigDecimal lineTotal = pricePerUnit.multiply(BigDecimal.valueOf(request.units()));
            booking.addLineItem(BookingLineItem.builder()
                    .stayDate(night)
                    .units(request.units())
                    .pricePerUnit(pricePerUnit)
                    .lineTotal(lineTotal)
                    .build());
            total = total.add(lineTotal);
        }
        booking.setTotalAmount(total);

        Booking saved = bookingRepository.save(booking);

        eventPublisher.publishEvent(new BookingCreatedEvent(
                saved.getBookingUid(), property.getPropertyUid(), roomType.getRoomTypeUid(),
                saved.getUnits(), nights.size(), saved.getHoldExpiresAt(), now));

        return toResponse(saved, guest.getGuestUid(), property.getPropertyUid(), roomType.getRoomTypeUid());
    }

    @Transactional(readOnly = true)
    public BookingResponse find(String bookingUid) {
        Booking booking = bookingRepository.findByBookingUid(bookingUid)
                .orElseThrow(() -> new BookingNotFoundException(bookingUid));
        return toResponse(booking,
                guestRepository.findById(booking.getGuestId()).map(Guest::getGuestUid).orElse(null),
                propertyRepository.findById(booking.getPropertyId()).map(Property::getPropertyUid).orElse(null),
                roomTypeRepository.findById(booking.getRoomTypeId()).map(RoomType::getRoomTypeUid).orElse(null));
    }

    private BookingResponse toResponse(Booking booking, String guestUid, String propertyUid, String roomTypeUid) {
        return BookingResponse.from(booking, guestUid, propertyUid, roomTypeUid);
    }

    /**
     * A first-time booker does not need a separate registration step. The guest row holds an
     * identifier and nothing else here — personal data belongs on the guest profile, never
     * inlined into the booking (design doc 12.6.3).
     */
    private Guest resolveGuest(String guestUid) {
        if (guestUid == null || guestUid.isBlank()) {
            return guestRepository.save(Guest.builder().build());
        }
        return guestRepository.findByGuestUid(guestUid)
                .orElseThrow(() -> new GuestNotFoundException(guestUid));
    }

    /**
     * Capacity is checked against units, not against a single room: two rooms sleeping two
     * each can take four guests, one room cannot.
     */
    private void requireCapacity(RoomType roomType, CreateBookingRequest request) {
        int guests = request.adults() + request.children();
        int capacity = roomType.getMaxGuests() * request.units();
        if (guests > capacity) {
            throw new GuestCapacityExceededException(
                    request.units() + " room(s) of type " + roomType.getName() + " sleep " + capacity
                            + " guest(s), but " + guests + " were requested");
        }
    }

    /**
     * Every night in the range must have an inventory row. A night beyond the materialised
     * horizon is not bookable, and from a guest's point of view that is simply unavailable.
     */
    private Map<LocalDate, DailyInventory> loadInventory(RoomType roomType, List<LocalDate> nights, String roomTypeUid) {
        List<DailyInventory> rows = dailyInventoryRepository.findByRoomTypeIdAndStayDateBetween(
                roomType.getId(), nights.get(0), nights.get(nights.size() - 1));

        Map<LocalDate, DailyInventory> byNight = new HashMap<>(rows.size());
        for (DailyInventory row : rows) {
            byNight.put(row.getStayDate(), row);
        }
        for (LocalDate night : nights) {
            if (!byNight.containsKey(night)) {
                throw new InventoryUnavailableException(roomTypeUid, night);
            }
        }
        return byNight;
    }
}
