package com.telecom.orders.repository;

import com.telecom.orders.entity.CustomerOrder;
import com.telecom.orders.entity.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<CustomerOrder, UUID> {

    Page<CustomerOrder> findByStatus(OrderStatus status, Pageable pageable);

    List<CustomerOrder> findByEmail(String email);
}
