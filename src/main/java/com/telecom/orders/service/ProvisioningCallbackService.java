package com.telecom.orders.service;

import com.telecom.orders.dto.ProvisioningCallback;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Service;

/** Partner callbacks may be retried and arrive concurrently: idempotent and race-safe. */
@Service
public class ProvisioningCallbackService {

    private final OrderTransactions orders;

    public ProvisioningCallbackService(OrderTransactions orders) {
        this.orders = orders;
    }

    /** @return true if this callback changed the order, false for a duplicate/late/losing callback */
    public boolean handle(ProvisioningCallback callback) {
        try {
            return orders.completeProvisioning(callback.orderId(), callback.success(), callback.iccid(),
                    callback.message()).isPresent();
        } catch (ConcurrencyFailureException concurrentWinner) {
            // @Version conflict (ObjectOptimisticLockingFailureException) or a lock conflict reported by the
            // database: another callback for the same order committed first
            return false;
        }
    }
}
