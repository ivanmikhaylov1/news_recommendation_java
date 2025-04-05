package com.example.demo.repository;

import com.example.demo.domain.model.NotificationSchedule;
import com.example.demo.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationScheduleRepository extends JpaRepository<NotificationSchedule, Long> {
  List<NotificationSchedule> findByUserAndIsActiveTrue(User user);
}
