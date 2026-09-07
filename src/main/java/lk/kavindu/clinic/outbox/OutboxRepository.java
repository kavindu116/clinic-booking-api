package lk.kavindu.clinic.outbox;

import io.lettuce.core.dynamic.annotation.Param;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent,Long> {

    @Query(value = """
           SELECT * FROM outbox_events
           WHERE status = 'PENDING'
           ORDER BY created_at
           LIMIT :batchSize
           FOR UPDATE SKIP LOCKED
           """, nativeQuery = true)
    List<OutboxEvent> lockPendingBatch(@Param("batchSize") int batchSize);

    long countByStatus(OutboxStatus status);

    List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByCreatedAtAsc(String aggregateType,Long aggregateId);

    @Modifying
    @Query("DELETE FROM OutboxEvent e WHERE e.status = OutboxStatus.PUBLISHED AND e.publishedAt <: cutoff")
    int deletePublishedBefore(@Param("cutoff") Instant cutoff);

    @Query("SELECT e FROM  OutboxEvent e WHERE e.status = OutboxStatus.FAILED ORDER BY e.createdAt DESC ")
    List<OutboxEvent> findFailed(Pageable pageable);




}
