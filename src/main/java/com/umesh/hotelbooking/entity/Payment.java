package com.umesh.hotelbooking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Placeholder for the Payment aggregate — enough to give {@code PaymentRepository} a
 * concrete target. Payment state, method and the gateway SPI are built in the payment phase.
 */
@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_uid", unique = true, nullable = false, updatable = false, length = 36)
    private String paymentUid;

    @PrePersist
    private void onCreate() {
        if (paymentUid == null) {
            paymentUid = UUID.randomUUID().toString();
        }
    }
}
