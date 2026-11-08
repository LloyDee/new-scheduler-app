package org.volunteers.scheduler.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.volunteers.scheduler.model.VolunteerTransferAudit;

public interface VolunteerTransferAudits extends JpaRepository<VolunteerTransferAudit, Long> {}
