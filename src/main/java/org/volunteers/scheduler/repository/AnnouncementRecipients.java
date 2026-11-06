package org.volunteers.scheduler.repository;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.volunteers.scheduler.model.AnnouncementRecipient;
public interface AnnouncementRecipients extends JpaRepository<AnnouncementRecipient, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    List<AnnouncementRecipient> findByAnnouncementId(Long id);
    List<AnnouncementRecipient> findByVolunteerIdIn(Collection<Long> ids);
    void deleteByAnnouncementId(Long id);
}
