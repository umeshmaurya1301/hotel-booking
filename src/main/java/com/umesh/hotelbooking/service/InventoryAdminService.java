package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.InventoryProperties;
import com.umesh.hotelbooking.dto.ExtendHorizonRequest;
import com.umesh.hotelbooking.dto.InventoryResponse;
import com.umesh.hotelbooking.dto.MaterialisationResponse;
import com.umesh.hotelbooking.dto.RateOverrideRequest;
import com.umesh.hotelbooking.dto.RepriceRequest;
import com.umesh.hotelbooking.dto.RepriceResponse;
import com.umesh.hotelbooking.entity.DailyInventory;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.exception.InvalidRequestException;
import com.umesh.hotelbooking.exception.InventoryNotMaterialisedException;
import com.umesh.hotelbooking.exception.PropertyNotFoundException;
import com.umesh.hotelbooking.exception.RoomTypeNotFoundException;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.repository.RoomTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

/**
 * Administrative operations over materialised inventory: rolling the horizon forward,
 * repricing a range under a strategy, and overriding a single night.
 */
@Service
public class InventoryAdminService {

    private final PropertyRepository propertyRepository;
    private final RoomTypeRepository roomTypeRepository;
    private final DailyInventoryRepository dailyInventoryRepository;
    private final InventoryMaterializer inventoryMaterializer;
    private final PricingStrategyRegistry pricingStrategyRegistry;
    private final InventoryProperties inventoryProperties;
    private final Clock clock;

    public InventoryAdminService(PropertyRepository propertyRepository,
                                 RoomTypeRepository roomTypeRepository,
                                 DailyInventoryRepository dailyInventoryRepository,
                                 InventoryMaterializer inventoryMaterializer,
                                 PricingStrategyRegistry pricingStrategyRegistry,
                                 InventoryProperties inventoryProperties,
                                 Clock clock) {
        this.propertyRepository = propertyRepository;
        this.roomTypeRepository = roomTypeRepository;
        this.dailyInventoryRepository = dailyInventoryRepository;
        this.inventoryMaterializer = inventoryMaterializer;
        this.pricingStrategyRegistry = pricingStrategyRegistry;
        this.inventoryProperties = inventoryProperties;
        this.clock = clock;
    }

    /**
     * Extends every room type of the named property — or of every property — so that
     * inventory exists for the next {@code horizonDays} nights.
     *
     * <p>Only missing nights are created. The already-materialised part of the range keeps
     * its existing prices, including manual overrides, because materialisation skips rows
     * that exist rather than rewriting them.
     */
    @Transactional
    public MaterialisationResponse extendHorizon(ExtendHorizonRequest request) {
        PricingStrategy strategy = pricingStrategyRegistry.resolveOrDefault(request.pricingStrategyCode());
        int horizonDays = request.horizonDays() == null
                ? inventoryProperties.horizonDays()
                : request.horizonDays();

        List<Property> properties = resolveProperties(request.propertyUid());

        int roomTypesTouched = 0;
        int nightsCreated = 0;
        LocalDate lastHorizonEnd = null;

        for (Property property : properties) {
            // Each property's horizon is measured in its own timezone (design doc 4.5).
            LocalDate today = LocalDate.now(clock.withZone(property.zone()));
            LocalDate horizonEnd = today.plusDays(horizonDays);
            lastHorizonEnd = horizonEnd;

            for (RoomType roomType : roomTypeRepository.findByPropertyId(property.getId())) {
                roomTypesTouched++;
                nightsCreated += inventoryMaterializer.materialise(
                        roomType, property.getCurrency(), today, horizonEnd, strategy);
            }
        }

        return new MaterialisationResponse(properties.size(), roomTypesTouched, nightsCreated, lastHorizonEnd);
    }

    /**
     * Re-runs a strategy over existing rows in {@code [from, to]} inclusive.
     *
     * <p>Rows outside the materialised horizon are not created here — repricing changes
     * prices, extending creates nights, and conflating the two would let a typo'd date range
     * silently materialise a year of inventory.
     */
    @Transactional
    public RepriceResponse reprice(RepriceRequest request) {
        if (request.to().isBefore(request.from())) {
            throw new InvalidRequestException("'to' must not be before 'from'");
        }
        RoomType roomType = requireRoomType(request.roomTypeUid());
        PricingStrategy strategy = pricingStrategyRegistry.resolve(request.strategyCode());

        List<DailyInventory> rows = dailyInventoryRepository
                .findByRoomTypeIdAndStayDateBetween(roomType.getId(), request.from(), request.to());

        for (DailyInventory row : rows) {
            row.setPricePerUnit(strategy.priceFor(roomType, row.getStayDate()));
        }

        return new RepriceResponse(
                roomType.getRoomTypeUid(), request.from(), request.to(),
                strategy.strategyCode(), rows.size());
    }

    /**
     * Overrides one night's price and/or unit count.
     *
     * <p>Lowering {@code totalUnits} below what is already booked for that night is rejected
     * with a clear error. The database check constraint would refuse it anyway — this just
     * turns a constraint violation into an answer the operator can act on.
     */
    @Transactional
    public InventoryResponse overrideNight(String roomTypeUid, RateOverrideRequest request) {
        if (request.pricePerUnit() == null && request.totalUnits() == null) {
            throw new InvalidRequestException("supply at least one of pricePerUnit or totalUnits");
        }
        RoomType roomType = requireRoomType(roomTypeUid);

        DailyInventory row = dailyInventoryRepository
                .findByRoomTypeIdAndStayDate(roomType.getId(), request.stayDate())
                .orElseThrow(() -> new InventoryNotMaterialisedException(roomTypeUid, request.stayDate()));

        if (request.pricePerUnit() != null) {
            row.setPricePerUnit(request.pricePerUnit());
        }
        if (request.totalUnits() != null) {
            if (request.totalUnits() < row.getBookedUnits()) {
                throw new InvalidRequestException(
                        "cannot reduce totalUnits to " + request.totalUnits()
                                + " on " + request.stayDate() + ": " + row.getBookedUnits()
                                + " units are already booked");
            }
            row.setTotalUnits(request.totalUnits());
        }
        return InventoryResponse.from(row);
    }

    /** Mapped inside the transaction, per the same rule as {@code PropertyOnboardingService}. */
    @Transactional(readOnly = true)
    public List<InventoryResponse> view(String roomTypeUid, LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new InvalidRequestException("'to' must not be before 'from'");
        }
        RoomType roomType = requireRoomType(roomTypeUid);
        return dailyInventoryRepository.findByRoomTypeIdAndStayDateBetween(roomType.getId(), from, to)
                .stream()
                .map(InventoryResponse::from)
                .toList();
    }

    private List<Property> resolveProperties(String propertyUid) {
        if (propertyUid == null || propertyUid.isBlank()) {
            return propertyRepository.findAll();
        }
        return List.of(propertyRepository.findByPropertyUid(propertyUid)
                .orElseThrow(() -> new PropertyNotFoundException(propertyUid)));
    }

    private RoomType requireRoomType(String roomTypeUid) {
        return roomTypeRepository.findByRoomTypeUid(roomTypeUid)
                .orElseThrow(() -> new RoomTypeNotFoundException(roomTypeUid));
    }
}
