package org.volunteers.scheduler.repository;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.volunteers.scheduler.model.Announcement;
import java.util.List;

public interface Announcements extends JpaRepository<Announcement, Long> {
    List<Announcement> findByEventIdOrderByCreatedAtDescIdDesc(Long eventId);
    @Query("""
        select count(a) from Announcement a where
        (a.eventId is null and not exists (select r from AnnouncementRead r where r.deviceId = :device and r.announcementId = a.id))
        or (a.eventId = :eventId and exists (select r.id from AnnouncementRecipient r, Volunteer v
            where r.announcementId = a.id and r.volunteerId = v.id and v.normalizedPhone = :phone and r.readAt is null))
        """)
    long unreadCountFor(@Param("device") String device, @Param("eventId") Long eventId, @Param("phone") String phone);
    @Query("select a from Announcement a where a.eventId is null and not exists (select r from AnnouncementRead r where r.deviceId = :device and r.announcementId = a.id) order by a.createdAt desc, a.id desc")
    List<Announcement> unread(@Param("device") String device);

    @Query("select count(a) from Announcement a where a.eventId is null and not exists (select r from AnnouncementRead r where r.deviceId = :device and r.announcementId = a.id)")
    long unreadCount(@Param("device") String device);
}
