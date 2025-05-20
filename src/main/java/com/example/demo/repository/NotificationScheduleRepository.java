package com.example.demo.repository;

import com.example.demo.domain.model.NotificationSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationScheduleRepository extends JpaRepository<NotificationSchedule, Long> {
  @Query("SELECT s FROM NotificationSchedule s WHERE s.user.id = :userId AND s.isActive = true")
  Optional<NotificationSchedule> findActiveByUserId(Long userId);

  @Modifying
  @Query("DELETE FROM NotificationSchedule s WHERE s.user.id = :userId AND s.isActive = false")
  void deleteInactiveByUserId(Long userId);

  @Modifying
  @Query("DELETE FROM NotificationSchedule s WHERE s.user.id = :userId")
  void deleteByUserId(Long userId);
}
