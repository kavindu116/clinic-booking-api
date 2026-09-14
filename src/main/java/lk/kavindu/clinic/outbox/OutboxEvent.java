package lk.kavindu.clinic.outbox;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false,length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id",nullable = false)
    private Long aggregateId;

    @Column(name = "event_type",nullable = false,length = 80)
    private String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false,columnDefinition = "jsonb")
    private String payload;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false,length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Builder.Default
    @Column(nullable = false)
    private Short attempts = 0;

    @Column(name = "last_error",length = 500)
    private String lastError;

    @Column(name = "created_at",nullable = false,updatable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
    }

    public void markPublished() {
        this.status = OutboxStatus.PUBLISHED;
        this.publishedAt = Instant.now();
        this.lastError = null;
    }

    public void markAttemptFailed(String error,int maxAttempts) {
        this.attempts = (short) (this.attempts + 1);
        this.lastError = error == null ? null
                :error.substring(0,Math.min(error.length(),500));
        if (this.attempts >= maxAttempts) {
            this.status = OutboxStatus.FAILED;
        }
    }
}
