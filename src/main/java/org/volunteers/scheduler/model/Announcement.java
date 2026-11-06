package org.volunteers.scheduler.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "scheduler_announcement", indexes = @Index(name = "idx_announcement_event", columnList = "event_id"))
public class Announcement {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "event_id") public Long eventId;
    public Long departmentId;
    @Column(length = 160) public String departmentName;
    @Column(length = 160) public String eventName;
    @Column(length = 160) public String title;
    @Column(length = 160) public String sender;
    @Column(nullable = false, columnDefinition = "boolean default false")
    public boolean acknowledgmentRequired;
    @Column(nullable = false, length = 5000)
    public String message;
    @Column(nullable = false)
    public Instant createdAt;
}
