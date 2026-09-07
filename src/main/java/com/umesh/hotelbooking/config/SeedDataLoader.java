package com.umesh.hotelbooking.config;

import com.umesh.hotelbooking.dto.OnboardPropertyRequest;
import com.umesh.hotelbooking.dto.PropertyResponse;
import com.umesh.hotelbooking.dto.RoomTypeRequest;
import com.umesh.hotelbooking.entity.Amenity;
import com.umesh.hotelbooking.repository.PropertyRepository;
import com.umesh.hotelbooking.service.PropertyOnboardingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Loads a deterministic demo dataset under {@code --spring.profiles.active=demo}.
 *
 * <p>Seeding goes through {@link PropertyOnboardingService} rather than a {@code data.sql}
 * fixture on purpose. Onboarding materialises a 90-night horizon per room type, so a SQL
 * fixture would have to hand-write several thousand inventory rows and would drift from the
 * materialisation logic the moment pricing changed. Driving the real service keeps the seed
 * consistent by construction and exercises the onboarding path on every demo boot.
 *
 * <p>The set is small (10 properties across 6 cities) but deliberately varied in city, star
 * rating, amenities and price band, so the search filters of a later phase have something
 * meaningful to discriminate on. It also covers both ownership shapes: a three-property
 * chain sharing one group, and seven independents that each get a group of exactly one.
 */
@Component
@Profile("demo")
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);
    private static final String ZONE = "Asia/Kolkata";

    private final PropertyOnboardingService onboardingService;
    private final PropertyRepository propertyRepository;

    public SeedDataLoader(PropertyOnboardingService onboardingService, PropertyRepository propertyRepository) {
        this.onboardingService = onboardingService;
        this.propertyRepository = propertyRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (propertyRepository.count() > 0) {
            log.info("Seed data skipped: {} properties already present", propertyRepository.count());
            return;
        }

        // A chain: the first onboarding creates the group, the rest attach to it by uid.
        PropertyResponse flagship = onboardingService.onboard(chainProperty(
                "Meridian Bengaluru", "Bengaluru", "MG Road", 5,
                Set.of(Amenity.WIFI, Amenity.POOL, Amenity.GYM, Amenity.SPA, Amenity.RESTAURANT,
                        Amenity.BAR, Amenity.AIRPORT_SHUTTLE, Amenity.BUSINESS_CENTRE),
                List.of(roomType("Deluxe King", 12, 2, "8000.00"),
                        roomType("Executive Suite", 6, 4, "15000.00"))));
        String chainGroupUid = flagship.propertyGroupUid();
        String chainOwnerUid = flagship.ownerUid();

        onboardingService.onboard(chainMember(
                "Meridian Mumbai", "Mumbai", "Bandra Kurla Complex", 5,
                Set.of(Amenity.WIFI, Amenity.POOL, Amenity.GYM, Amenity.RESTAURANT,
                        Amenity.BAR, Amenity.BUSINESS_CENTRE),
                List.of(roomType("Deluxe King", 20, 2, "11000.00"),
                        roomType("Sea View Suite", 8, 4, "22000.00")),
                chainGroupUid, chainOwnerUid));

        onboardingService.onboard(chainMember(
                "Meridian Delhi", "New Delhi", "Aerocity", 5,
                Set.of(Amenity.WIFI, Amenity.GYM, Amenity.RESTAURANT, Amenity.AIRPORT_SHUTTLE,
                        Amenity.BUSINESS_CENTRE),
                List.of(roomType("Deluxe King", 18, 2, "9500.00"),
                        roomType("Club Room", 10, 3, "14000.00")),
                chainGroupUid, chainOwnerUid));

        // Independents: no group named, so each gets a group of exactly one.
        onboardingService.onboard(independent(
                "Lakeview Residency", "Bengaluru", "Indiranagar", 4,
                Set.of(Amenity.WIFI, Amenity.RESTAURANT, Amenity.PARKING, Amenity.AIR_CONDITIONING),
                List.of(roomType("Standard Double", 15, 2, "4200.00"),
                        roomType("Family Room", 5, 4, "6800.00"))));

        onboardingService.onboard(independent(
                "Colaba Boutique", "Mumbai", "Colaba", 4,
                Set.of(Amenity.WIFI, Amenity.RESTAURANT, Amenity.BAR, Amenity.PET_FRIENDLY),
                List.of(roomType("Heritage Room", 10, 2, "7300.00"))));

        onboardingService.onboard(independent(
                "Pink City Haveli", "Jaipur", "Amer Road", 4,
                Set.of(Amenity.WIFI, Amenity.POOL, Amenity.RESTAURANT, Amenity.PARKING,
                        Amenity.BREAKFAST_INCLUDED),
                List.of(roomType("Haveli Room", 14, 2, "5500.00"),
                        roomType("Royal Suite", 4, 4, "12500.00"))));

        onboardingService.onboard(independent(
                "Rajputana Inn", "Jaipur", "Bani Park", 3,
                Set.of(Amenity.WIFI, Amenity.PARKING, Amenity.AIR_CONDITIONING),
                List.of(roomType("Standard Room", 20, 2, "2600.00"))));

        onboardingService.onboard(independent(
                "Anjuna Beach Resort", "Goa", "Anjuna", 4,
                Set.of(Amenity.WIFI, Amenity.POOL, Amenity.BAR, Amenity.RESTAURANT,
                        Amenity.PET_FRIENDLY, Amenity.BREAKFAST_INCLUDED),
                List.of(roomType("Garden Cottage", 12, 2, "6400.00"),
                        roomType("Beachfront Villa", 4, 6, "18000.00"))));

        onboardingService.onboard(independent(
                "Palolem Sands", "Goa", "Palolem", 3,
                Set.of(Amenity.WIFI, Amenity.RESTAURANT, Amenity.BAR),
                List.of(roomType("Beach Hut", 18, 2, "3100.00"))));

        onboardingService.onboard(independent(
                "Marina Grand", "Chennai", "Adyar", 4,
                Set.of(Amenity.WIFI, Amenity.POOL, Amenity.GYM, Amenity.RESTAURANT,
                        Amenity.BUSINESS_CENTRE, Amenity.AIRPORT_SHUTTLE),
                List.of(roomType("Business Room", 16, 2, "6900.00"),
                        roomType("Executive Suite", 6, 3, "13500.00"))));

        onboardingService.onboard(independent(
                "Besant Nagar Stay", "Chennai", "Besant Nagar", 3,
                Set.of(Amenity.WIFI, Amenity.PARKING, Amenity.AIR_CONDITIONING, Amenity.ROOM_SERVICE),
                List.of(roomType("Standard Room", 22, 2, "2900.00"))));

        log.info("Seed data loaded: {} properties", propertyRepository.count());
    }

    private OnboardPropertyRequest chainProperty(String name, String city, String locality, int stars,
                                                 Set<Amenity> amenities, List<RoomTypeRequest> roomTypes) {
        return new OnboardPropertyRequest(
                null, "Meridian Hotels Pvt Ltd", "operators@meridian.example",
                null, "Meridian Hotels", "HDFC",
                name, city, locality, null, null, stars, ZONE, "INR", amenities,
                roomTypes, null);
    }

    private OnboardPropertyRequest chainMember(String name, String city, String locality, int stars,
                                               Set<Amenity> amenities, List<RoomTypeRequest> roomTypes,
                                               String groupUid, String ownerUid) {
        return new OnboardPropertyRequest(
                ownerUid, null, null,
                groupUid, null, null,
                name, city, locality, null, null, stars, ZONE, "INR", amenities,
                roomTypes, null);
    }

    private OnboardPropertyRequest independent(String name, String city, String locality, int stars,
                                               Set<Amenity> amenities, List<RoomTypeRequest> roomTypes) {
        return new OnboardPropertyRequest(
                null, name + " Owner", null,
                null, null, null,
                name, city, locality, null, null, stars, ZONE, "INR", amenities,
                roomTypes, null);
    }

    private static RoomTypeRequest roomType(String name, int units, int maxGuests, String basePrice) {
        return new RoomTypeRequest(name, units, maxGuests, new BigDecimal(basePrice));
    }
}
