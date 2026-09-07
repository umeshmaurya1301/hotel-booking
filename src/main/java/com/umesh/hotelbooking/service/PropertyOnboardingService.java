package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.InventoryProperties;
import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.dto.UpdatePropertyRequest;
import com.umesh.hotelbooking.entity.Owner;
import com.umesh.hotelbooking.entity.Property;
import com.umesh.hotelbooking.entity.PropertyGroup;
import com.umesh.hotelbooking.entity.RoomType;
import com.umesh.hotelbooking.event.PropertyOnboardedEvent;
import com.umesh.hotelbooking.exception.InvalidRequestException;
import com.umesh.hotelbooking.exception.OwnerNotFoundException;
import com.umesh.hotelbooking.exception.PropertyGroupNotFoundException;
import com.umesh.hotelbooking.exception.PropertyNotFoundException;
import com.umesh.hotelbooking.repository.OwnerRepository;
import com.umesh.hotelbooking.repository.PropertyGroupRepository;
import com.umesh.hotelbooking.repository.PropertyRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashSet;

/**
 * Onboards a property and opens its inventory horizon (design doc 4.3).
 *
 * <p>The whole flow is one transaction. A property whose inventory failed to materialise
 * would be visible in search but unbookable on every date, which is worse than not existing:
 * either both land or neither does.
 */
@Service
public class PropertyOnboardingService {

    private final OwnerRepository ownerRepository;
    private final PropertyGroupRepository propertyGroupRepository;
    private final PropertyRepository propertyRepository;
    private final InventoryMaterializer inventoryMaterializer;
    private final PricingStrategyRegistry pricingStrategyRegistry;
    private final InventoryProperties inventoryProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public PropertyOnboardingService(OwnerRepository ownerRepository,
                                     PropertyGroupRepository propertyGroupRepository,
                                     PropertyRepository propertyRepository,
                                     InventoryMaterializer inventoryMaterializer,
                                     PricingStrategyRegistry pricingStrategyRegistry,
                                     InventoryProperties inventoryProperties,
                                     ApplicationEventPublisher eventPublisher,
                                     Clock clock) {
        this.ownerRepository = ownerRepository;
        this.propertyGroupRepository = propertyGroupRepository;
        this.propertyRepository = propertyRepository;
        this.inventoryMaterializer = inventoryMaterializer;
        this.pricingStrategyRegistry = pricingStrategyRegistry;
        this.inventoryProperties = inventoryProperties;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Returns a response DTO rather than the entity on purpose. {@code open-in-view} is off,
     * so a {@link Property} handed back to a controller would be detached and its lazy
     * associations — group, owner, room types, amenities — would blow up on first access.
     * Mapping happens here, inside the transaction, which is the only place it is safe.
     */
    @Transactional
    public PropertyResponse onboard(OnboardPropertyRequest request) {
        ZoneId zone = parseZone(request.zoneId());

        Owner owner = resolveOwner(request);
        PropertyGroup group = resolveGroup(request, owner);

        Property property = buildProperty(request, group);
        for (RoomTypeRequest roomTypeRequest : request.roomTypes()) {
            property.addRoomType(buildRoomType(roomTypeRequest));
        }
        Property saved = propertyRepository.save(property);

        int nightsMaterialised = materialiseOpeningHorizon(saved, zone, request.pricingStrategyCode());

        eventPublisher.publishEvent(new PropertyOnboardedEvent(
                saved.getPropertyUid(),
                group.getPropertyGroupUid(),
                saved.getCity(),
                saved.getRoomTypes().size(),
                nightsMaterialised,
                clock.instant()));

        return PropertyResponse.from(saved);
    }

    @Transactional
    public PropertyResponse update(String propertyUid, UpdatePropertyRequest request) {
        Property property = propertyRepository.findByPropertyUid(propertyUid)
                .orElseThrow(() -> new PropertyNotFoundException(propertyUid));

        if (request.name() != null) {
            property.setName(request.name());
        }
        if (request.locality() != null) {
            property.setLocality(request.locality());
        }
        if (request.starRating() != null) {
            property.setStarRating(request.starRating());
        }
        if (request.amenities() != null) {
            property.setAmenities(new LinkedHashSet<>(request.amenities()));
        }
        return PropertyResponse.from(property);
    }

    @Transactional(readOnly = true)
    public PropertyResponse findByUid(String propertyUid) {
        return PropertyResponse.from(propertyRepository.findByPropertyUid(propertyUid)
                .orElseThrow(() -> new PropertyNotFoundException(propertyUid)));
    }

    /**
     * Attach to the named owner, or create one. Mirrors the group rule below: the caller
     * supplies an identifier for something that exists, or the details to bring it into
     * existence, and never both obligations at once.
     */
    private Owner resolveOwner(OnboardPropertyRequest request) {
        if (request.ownerUid() != null && !request.ownerUid().isBlank()) {
            return ownerRepository.findByOwnerUid(request.ownerUid())
                    .orElseThrow(() -> new OwnerNotFoundException(request.ownerUid()));
        }
        if (request.ownerName() == null || request.ownerName().isBlank()) {
            throw new InvalidRequestException("either ownerUid or ownerName must be supplied");
        }
        return ownerRepository.save(Owner.builder()
                .name(request.ownerName())
                .email(request.ownerEmail())
                .build());
    }

    /**
     * The structural requirement from the brief, in one method (design doc 3.1).
     *
     * <p>A chain names its existing group and the property attaches to it. An independent
     * hotel names none and gets a group containing exactly one property. Both paths end with
     * a property that has a group, so no code past this point — search, inventory, booking,
     * settlement — ever asks whether it is dealing with a chain. The branch exists here, once,
     * and it is about attaching versus creating, not about two different shapes of the world.
     */
    private PropertyGroup resolveGroup(OnboardPropertyRequest request, Owner owner) {
        if (request.propertyGroupUid() != null && !request.propertyGroupUid().isBlank()) {
            return propertyGroupRepository.findByPropertyGroupUid(request.propertyGroupUid())
                    .orElseThrow(() -> new PropertyGroupNotFoundException(request.propertyGroupUid()));
        }
        String groupName = (request.propertyGroupName() == null || request.propertyGroupName().isBlank())
                ? request.name()
                : request.propertyGroupName();
        return propertyGroupRepository.save(PropertyGroup.builder()
                .name(groupName)
                .owner(owner)
                .settlementBankCode(request.settlementBankCode())
                .build());
    }

    private Property buildProperty(OnboardPropertyRequest request, PropertyGroup group) {
        return Property.builder()
                .propertyGroup(group)
                .name(request.name())
                .city(request.city())
                .locality(request.locality())
                .latitude(request.latitude())
                .longitude(request.longitude())
                .starRating(request.starRating())
                .zoneId(request.zoneId())
                .currency(request.currency() == null || request.currency().isBlank()
                        ? "INR" : request.currency().toUpperCase())
                .amenities(request.amenities() == null
                        ? new LinkedHashSet<>() : new LinkedHashSet<>(request.amenities()))
                .build();
    }

    private RoomType buildRoomType(RoomTypeRequest request) {
        return RoomType.builder()
                .name(request.name())
                .totalUnits(request.totalUnits())
                .maxGuests(request.maxGuests())
                .basePricePerNight(request.basePricePerNight())
                .build();
    }

    /**
     * Opens the horizon from <em>the property's</em> today, not the server's. A UTC server
     * onboarding an Asia/Kolkata hotel at 19:00 UTC is already on the next calendar day
     * there; starting from the server's date would leave that first night unmaterialised and
     * therefore unbookable (design doc 4.5).
     */
    private int materialiseOpeningHorizon(Property property, ZoneId zone, String strategyCode) {
        PricingStrategy strategy = pricingStrategyRegistry.resolveOrDefault(strategyCode);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        LocalDate horizonEnd = today.plusDays(inventoryProperties.horizonDays());

        int nights = 0;
        for (RoomType roomType : property.getRoomTypes()) {
            nights += inventoryMaterializer.materialise(
                    roomType, property.getCurrency(), today, horizonEnd, strategy);
        }
        return nights;
    }

    private ZoneId parseZone(String zoneId) {
        try {
            // Covers both an unparseable id and a well-formed one with no zone rules;
            // ZoneRulesException is a DateTimeException, so this single catch gets both.
            return ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new InvalidRequestException("zoneId is not a known IANA zone: " + zoneId);
        }
    }
}
