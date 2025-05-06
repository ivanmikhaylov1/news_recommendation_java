package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
@Table(name = "notification_schedules")
public class NotificationSchedule {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "schedule_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(name = "start_hour", nullable = false)
  private Integer startHour = 12;

  @Column(name = "end_hour", nullable = false)
  private Integer endHour = 20;

  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  public static NotificationSchedule createDefaultSchedule(User user) {
    NotificationSchedule schedule = new NotificationSchedule();
    schedule.setUser(user);
    schedule.setStartHour(12);
    schedule.setEndHour(20);
    schedule.setIsActive(true);
    return schedule;
  }
}
