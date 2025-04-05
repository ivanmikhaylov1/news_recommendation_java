package com.example.demo.service;

import com.example.demo.domain.dto.NotificationScheduleDTO;
import com.example.demo.domain.model.NotificationSchedule;
import com.example.demo.domain.model.User;
import com.example.demo.repository.NotificationScheduleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationScheduleService {
  private final NotificationScheduleRepository scheduleRepository;

  @Transactional
  public void updateNotificationTime(User user, Integer startHour, Integer endHour) {
    NotificationSchedule schedule;
    List<NotificationSchedule> activeSchedules = scheduleRepository.findByUserAndIsActiveTrue(user);
    if (activeSchedules.isEmpty()) {
      schedule = NotificationSchedule.createDefaultSchedule(user);
    } else {
      schedule = activeSchedules.get(0);
    }

    schedule.setStartHour(startHour);
    schedule.setEndHour(endHour);
    schedule = scheduleRepository.save(schedule);
    NotificationScheduleDTO.fromEntity(schedule);
  }

  @Transactional
  public void toggleScheduleActive(User user, boolean isActive) {
    NotificationSchedule schedule;
    List<NotificationSchedule> activeSchedules = scheduleRepository.findByUserAndIsActiveTrue(user);
    if (activeSchedules.isEmpty()) {
      schedule = NotificationSchedule.createDefaultSchedule(user);
    } else {
      schedule = activeSchedules.get(0);
    }

    schedule.setIsActive(isActive);
    schedule = scheduleRepository.save(schedule);
    NotificationScheduleDTO.fromEntity(schedule);
  }

  @Transactional
  public NotificationScheduleDTO getActiveScheduleDTO(User user) {
    List<NotificationSchedule> activeSchedules = scheduleRepository.findByUserAndIsActiveTrue(user);
    NotificationSchedule schedule;
    if (activeSchedules.isEmpty()) {
      schedule = NotificationSchedule.createDefaultSchedule(user);
      schedule = scheduleRepository.save(schedule);
    } else {
      schedule = activeSchedules.get(0);
    }

    return NotificationScheduleDTO.fromEntity(schedule);
  }
}
