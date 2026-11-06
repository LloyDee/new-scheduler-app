package org.volunteers.scheduler.model;
import jakarta.persistence.*;
import java.time.Instant;
/** Scalar IDs preserve the send-time audience after membership deletion or restore. */
@Entity
@Table(name = "scheduler_announcement_recipient", indexes = @Index(name = "idx_announcement_recipient_volunteer", columnList = "volunteer_id"),
    uniqueConstraints = @UniqueConstraint(columnNames = {"announcement_id", "volunteer_id"}))
public class AnnouncementRecipient {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) public Long id;
    @Column(name = "announcement_id", nullable = false) public Long announcementId;
    @Column(name = "volunteer_id", nullable = false) public Long volunteerId;
    public Instant readAt;
    public Instant acknowledgedAt;
}
