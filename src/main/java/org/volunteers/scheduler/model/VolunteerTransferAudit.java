package org.volunteers.scheduler.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Immutable snapshot; retained even when an event or profile is deleted. */
@Entity
public class VolunteerTransferAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private Instant performedAt;
    private String actor;
    private Long sourceEventId;
    private Long destinationEventId;
    private Long volunteerId;
    private String volunteerName;
    private String action;
    @Column(length = 4000) private String roleChanges;
    protected VolunteerTransferAudit() {}
    public VolunteerTransferAudit(String actor, Volunteer volunteer, Long destination, String action, String roles) {
        this.performedAt = Instant.now(); this.actor = actor;
        this.sourceEventId = volunteer.getEvent().getId(); this.destinationEventId = destination;
        this.volunteerId = volunteer.getId(); this.volunteerName = volunteer.getName();
        this.action = action; this.roleChanges = roles;
    }
}
