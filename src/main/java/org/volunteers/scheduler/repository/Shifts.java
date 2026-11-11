package org.volunteers.scheduler.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.volunteers.scheduler.model.Shift;

public interface Shifts extends JpaRepository<Shift, Long> {
    List<Shift> findByDepartmentEventIdOrderByDateAscStartTimeAscIdAsc(Long eventId);
    // COALESCE gives PostgreSQL a date type even when the filter is empty.
    String FILTER = """
        from Shift s where s.department.event.id = :eventId
        and (:departmentId is null or s.department.id = :departmentId)
        and s.date = coalesce(:date, s.date)
        and (:view <> 'mine' or exists (select a.id from Assignment a where a.shift = s and a.volunteer.id in :ownIds))
        and (:view <> 'needs' or s.requiredCount > (select count(a) from Assignment a where a.shift = s))
        """;
    @org.springframework.data.jpa.repository.Query(value = "select s " + FILTER + " order by s.date, s.startTime, s.id", countQuery = "select count(s) " + FILTER)
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"department.event"})
    org.springframework.data.domain.Page<Shift> findPage(long eventId, Long departmentId, java.time.LocalDate date,
        String view, java.util.Collection<Long> ownIds, org.springframework.data.domain.Pageable pageable);
    @org.springframework.data.jpa.repository.Query("select distinct s.date from Shift s where s.department.event.id = :eventId")
    List<java.time.LocalDate> findDates(long eventId);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"department.event"})
    List<Shift> findByDepartmentIdInOrderByDateAscStartTimeAscIdAsc(java.util.Collection<Long> ids);
    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"department.event"})
    @org.springframework.data.jpa.repository.Query("""
        select s from Shift s where s.department.event.id = :eventId
        and (s.date > :date or (s.date = :date and s.endTime > :time))
        and exists (select a.id from Assignment a where a.shift = s and a.volunteer.id in :ids)
        order by s.date, s.startTime, s.id
        """)
    List<Shift> findUpcoming(long eventId, java.util.Collection<Long> ids, java.time.LocalDate date, java.time.LocalTime time);
    long countByDepartmentEventId(long eventId);
    @org.springframework.data.jpa.repository.Query("""
        select coalesce(sum(case when s.requiredCount > (select count(a) from Assignment a where a.shift = s)
        then s.requiredCount - (select count(a) from Assignment a where a.shift = s) else 0 end), 0)
        from Shift s where s.department.event.id = :eventId
        """)
    long countMissing(long eventId);
}
