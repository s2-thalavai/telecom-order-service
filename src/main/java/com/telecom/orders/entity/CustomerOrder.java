package com.telecom.orders.entity;

import com.telecom.orders.dto.PlaceOrderRequest;
import com.telecom.orders.dto.PlanDetails;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_orders", indexes = {
        @Index(name = "idx_orders_status", columnList = "status"),
        @Index(name = "idx_orders_email", columnList = "email")})
public class CustomerOrder {

    @Id
    private UUID id;

    @Column(name = "customer_name", nullable = false, length = 100)
    private String customerName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(name = "plan_code", nullable = false, length = 20)
    private String planCode;

    @Column(name = "monthly_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column(nullable = false, length = 3)
    private String circle;

    @Column(length = 15)
    private String msisdn;

    @Column(length = 22)
    private String iccid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "credit_score")
    private Integer creditScore;

    @Column(length = 255)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    /** Optimistic locking: two concurrent provisioning callbacks can't both transition the order. */
    @Version
    private Long version;

    protected CustomerOrder() {
    }

    private CustomerOrder(PlaceOrderRequest request, PlanDetails plan, int creditScore, OrderStatus status,
                          String msisdn, String reason) {
        this.id = UUID.randomUUID();
        this.customerName = request.customerName();
        this.email = request.email();
        this.planCode = plan.code();
        this.monthlyPrice = plan.monthlyPrice();
        this.circle = request.circle();
        this.creditScore = creditScore;
        this.status = status;
        this.msisdn = msisdn;
        this.reason = reason;
    }

    public static CustomerOrder provisioning(PlaceOrderRequest request, PlanDetails plan, int score, String msisdn) {
        return new CustomerOrder(request, plan, score, OrderStatus.PROVISIONING, msisdn, null);
    }

    public static CustomerOrder rejected(PlaceOrderRequest request, PlanDetails plan, int score, String reason) {
        return new CustomerOrder(request, plan, score, OrderStatus.REJECTED, null, reason);
    }

    public void activate(String newIccid) {
        this.iccid = newIccid;
        this.status = OrderStatus.ACTIVE;
    }

    public void fail(String newReason) {
        this.reason = newReason;
        this.status = OrderStatus.FAILED;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getCustomerName() { return customerName; }
    public String getEmail() { return email; }
    public String getPlanCode() { return planCode; }
    public BigDecimal getMonthlyPrice() { return monthlyPrice; }
    public String getCircle() { return circle; }
    public String getMsisdn() { return msisdn; }
    public String getIccid() { return iccid; }
    public OrderStatus getStatus() { return status; }
    public Integer getCreditScore() { return creditScore; }
    public String getReason() { return reason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
}
