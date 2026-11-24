package ru.otus.sn.config;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Выбирает master или один из слейвов в зависимости от типа транзакции:
 * readOnly-транзакции идут на слейвы (round-robin), остальные — на мастер.
 */
public class ReplicationRoutingDataSource extends AbstractRoutingDataSource {

    private final Object masterKey;
    private final List<Object> replicaKeys;
    private final AtomicInteger replicaCounter = new AtomicInteger();

    public ReplicationRoutingDataSource(javax.sql.DataSource masterDataSource, Object masterKey, List<Object> replicaKeys) {
        this.masterKey = masterKey;
        this.replicaKeys = replicaKeys;
        setDefaultTargetDataSource(masterDataSource);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        boolean readOnly = TransactionSynchronizationManager.isCurrentTransactionReadOnly();
        if (!readOnly || replicaKeys.isEmpty()) {
            return masterKey;
        }
        int idx = Math.floorMod(replicaCounter.getAndIncrement(), replicaKeys.size());
        return replicaKeys.get(idx);
    }
}
