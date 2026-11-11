package org.volunteers.scheduler.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.volunteers.scheduler.model.Volunteer;

public interface Volunteers extends JpaRepository<Volunteer, Long> {
    interface AccessRow {
        Long getVolunteerId();
        String getName();
        boolean getEventAdministrator();
        Long getEventId();
        Long getDepartmentId();
        Volunteer.Role getRole();
    }

    @org.springframework.data.jpa.repository.Query("""
        select v.id as volunteerId, v.name as name, v.eventAdministrator as eventAdministrator, v.event.id as eventId,
               key(roles).id as departmentId, value(roles) as role
        from Volunteer v left join v.departmentRoles roles
        where v.normalizedPhone = :phone and v.participationEnded = false
        """)
    List<AccessRow> findAccessByPhone(@org.springframework.data.repository.query.Param("phone") String phone);

    String PAGE_FILTER = """
        from Volunteer v where v.event.id = :eventId and v.participationEnded = false
        and (:departmentId is null or exists
            (select member.id from Volunteer member join member.departmentRoles roles
             where member.id = v.id and key(roles).id = :departmentId))
        and locate(:search, lower(concat(v.name, ' ', coalesce(v.congregation, ''), ' ',
            coalesce(v.phone, ''), ' ', coalesce(v.email, '')))) > 0
        """;
    @org.springframework.data.jpa.repository.Query(value = "select v.id " + PAGE_FILTER + " order by v.name, v.id",
        countQuery = "select count(v) " + PAGE_FILTER)
    org.springframework.data.domain.Page<Long> findPageIds(
        @org.springframework.data.repository.query.Param("eventId") long eventId,
        @org.springframework.data.repository.query.Param("departmentId") Long departmentId,
        @org.springframework.data.repository.query.Param("search") String search,
        org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"event", "departmentRoles"})
    List<Volunteer> findByIdInOrderByNameAscIdAsc(java.util.Collection<Long> ids);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"event", "departmentRoles"})
    List<Volunteer> findByNormalizedPhone(String normalizedPhone);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"event", "departmentRoles"})
    List<Volunteer> findByEventIdOrderByNameAscIdAsc(Long eventId);
    long countByEventIdAndParticipationEndedFalse(long eventId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"event", "departmentRoles"})
    @org.springframework.data.jpa.repository.Query("""
        select distinct v from Volunteer v join v.departmentRoles roles
        where v.event.id = :eventId and v.participationEnded = false and key(roles).id in :ids
        """)
    List<Volunteer> findMembers(long eventId, java.util.Collection<Long> ids);
    interface Eligible { Long getShiftId(); Long getVolunteerId(); }
    @org.springframework.data.jpa.repository.Query("""
        select s.id as shiftId, v.id as volunteerId from Shift s, Volunteer v
        where s.id in :ids and v.event.id = :eventId and v.participationEnded = false
        and (v.departmentRoles is empty or exists
            (select m.id from Volunteer m join m.departmentRoles roles where m = v and key(roles) = s.department))
        and not exists (select a.id from Assignment a where a.volunteer = v and a.shift.date = s.date
            and a.shift.startTime < s.endTime and a.shift.endTime > s.startTime)
        """)
    List<Eligible> findAvailableForShifts(long eventId, java.util.Collection<Long> ids);
}