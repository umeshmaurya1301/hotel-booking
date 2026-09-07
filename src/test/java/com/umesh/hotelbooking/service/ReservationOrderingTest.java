package com.umesh.hotelbooking.service;

import com.umesh.hotelbooking.config.InventoryLockProperties;
import com.umesh.hotelbooking.exception.InventoryUnavailableException;
import com.umesh.hotelbooking.repository.DailyInventoryRepository;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the ordering discipline directly.
 *
 * <p>The concurrency suite shows that overlapping bookings all complete; this shows
 * <em>why</em>. Row locks are taken in the order the UPDATEs are issued, so the guarantee is
 * that nights are always issued ascending regardless of how the caller supplies them. That is
 * a property of the code, and asserting it deterministically is far more reliable than hoping
 * a load-dependent deadlock reproduces in a test run.
 */
class ReservationOrderingTest {

    private static final LocalDate DAY_1 = LocalDate.of(2026, 9, 10);

    private InventoryReservationService serviceWith(DailyInventoryRepository repository) {
        return new InventoryReservationService(repository,
                new InventoryLockRegistry(new InventoryLockProperties(true, Duration.ofSeconds(1))));
    }

    @Test
    void nightsAreReservedInAscendingOrderEvenWhenSuppliedOutOfOrder() {
        DailyInventoryRepository repository = mock(DailyInventoryRepository.class);
        when(repository.reserveUnits(eq(1L), org.mockito.ArgumentMatchers.any(), anyInt())).thenReturn(1);

        List<LocalDate> shuffled = List.of(
                DAY_1.plusDays(2), DAY_1, DAY_1.plusDays(3), DAY_1.plusDays(1));

        serviceWith(repository).reserve(1L, "rt-1", shuffled, 1);

        InOrder ordered = inOrder(repository);
        ordered.verify(repository).reserveUnits(1L, DAY_1, 1);
        ordered.verify(repository).reserveUnits(1L, DAY_1.plusDays(1), 1);
        ordered.verify(repository).reserveUnits(1L, DAY_1.plusDays(2), 1);
        ordered.verify(repository).reserveUnits(1L, DAY_1.plusDays(3), 1);
    }

    @Test
    void releasesFollowTheSameAscendingOrderAsReservations() {
        DailyInventoryRepository repository = mock(DailyInventoryRepository.class);
        when(repository.releaseUnits(eq(1L), org.mockito.ArgumentMatchers.any(), anyInt())).thenReturn(1);

        serviceWith(repository).release(1L, List.of(DAY_1.plusDays(2), DAY_1, DAY_1.plusDays(1)), 2);

        InOrder ordered = inOrder(repository);
        ordered.verify(repository).releaseUnits(1L, DAY_1, 2);
        ordered.verify(repository).releaseUnits(1L, DAY_1.plusDays(1), 2);
        ordered.verify(repository).releaseUnits(1L, DAY_1.plusDays(2), 2);
    }

    /**
     * A zero row count is the failure signal, and it must stop the loop immediately: any
     * night after the blocked one must never be touched, so the rollback has less to undo and
     * the error names the night that actually failed.
     */
    @Test
    void aZeroRowCountStopsTheLoopAndNamesTheFailedNight() {
        DailyInventoryRepository repository = mock(DailyInventoryRepository.class);
        when(repository.reserveUnits(1L, DAY_1, 1)).thenReturn(1);
        when(repository.reserveUnits(1L, DAY_1.plusDays(1), 1)).thenReturn(0);

        assertThatThrownBy(() -> serviceWith(repository).reserve(
                1L, "rt-1", List.of(DAY_1, DAY_1.plusDays(1), DAY_1.plusDays(2)), 1))
                .isInstanceOf(InventoryUnavailableException.class)
                .hasMessageContaining(DAY_1.plusDays(1).toString());

        verify(repository, never()).reserveUnits(1L, DAY_1.plusDays(2), 1);
    }

    @Test
    void releaseIsBestEffortAndReportsHowManyNightsItFreed() {
        DailyInventoryRepository repository = mock(DailyInventoryRepository.class);
        when(repository.releaseUnits(1L, DAY_1, 1)).thenReturn(1);
        // Already released - a double release must not abort the remaining nights.
        when(repository.releaseUnits(1L, DAY_1.plusDays(1), 1)).thenReturn(0);
        when(repository.releaseUnits(1L, DAY_1.plusDays(2), 1)).thenReturn(1);

        int released = serviceWith(repository).release(
                1L, List.of(DAY_1, DAY_1.plusDays(1), DAY_1.plusDays(2)), 1);

        assertThat(released).isEqualTo(2);
        verify(repository).releaseUnits(1L, DAY_1.plusDays(2), 1);
    }
}
