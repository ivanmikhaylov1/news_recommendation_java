package com.example.demo.service;

import com.example.demo.domain.dto.NotificationScheduleDTO;
import com.example.demo.domain.model.NotificationSchedule;
import com.example.demo.domain.model.User;
import com.example.demo.repository.NotificationScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationScheduleService {
  private final NotificationScheduleRepository scheduleRepository;
  private final UserService userService;

  @Transactional
  public void updateNotificationTime(Long userId, Integer startHour, Integer endHour) {
    try {
      User user = userService.getOrCreateUser(userId);

      scheduleRepository.deleteInactiveByUserId(userId);

      NotificationSchedule schedule;
      Optional<NotificationSchedule> existingSchedule = scheduleRepository.findActiveByUserId(userId);

      if (existingSchedule.isPresent()) {
        schedule = existingSchedule.get();
        schedule.setStartHour(startHour);
        schedule.setEndHour(endHour);
      } else {
        schedule = NotificationSchedule.createDefaultSchedule(user);
        schedule.setStartHour(startHour);
        schedule.setEndHour(endHour);
      }

      schedule = scheduleRepository.save(schedule);
    } catch (Exception e) {
      log.error("Ошибка при обновлении времени уведомлений для пользователя {}: {}", userId, e.getMessage());
      throw e;
    }
  }

  @Transactional
  public NotificationScheduleDTO toggleScheduleActive(Long userId, boolean isActive) {
    try {
      User user = userService.getOrCreateUser(userId);

      scheduleRepository.deleteInactiveByUserId(userId);

      NotificationSchedule schedule;
      Optional<NotificationSchedule> existingSchedule = scheduleRepository.findActiveByUserId(userId);

      if (existingSchedule.isPresent()) {
        schedule = existingSchedule.get();
        schedule.setIsActive(isActive);
      } else {
        schedule = NotificationSchedule.createDefaultSchedule(user);
        schedule.setIsActive(isActive);
      }

      schedule = scheduleRepository.save(schedule);
      log.info("Статус расписания для пользователя {} изменен на {}", userId, isActive);
      return NotificationScheduleDTO.fromEntity(schedule);
    } catch (Exception e) {
      log.error("Ошибка при переключении статуса уведомлений для пользователя {}: {}", userId, e.getMessage());
      throw e;
    }
  }

  @Transactional
  public NotificationScheduleDTO getActiveScheduleDTO(Long userId) {
    try {
      User user = userService.getOrCreateUser(userId);

      scheduleRepository.deleteInactiveByUserId(userId);

      Optional<NotificationSchedule> existingSchedule = scheduleRepository.findActiveByUserId(userId);
      NotificationSchedule schedule;

      if (existingSchedule.isPresent()) {
        schedule = existingSchedule.get();
        log.debug("Найдено существующее расписание для пользователя {}", userId);
      } else {
        schedule = NotificationSchedule.createDefaultSchedule(user);
        schedule.setIsActive(false);
        schedule = scheduleRepository.save(schedule);
        log.info("Создано новое неактивное расписание для пользователя {}", userId);
      }

      return NotificationScheduleDTO.fromEntity(schedule);
    } catch (Exception e) {
      log.error("Ошибка при получении расписания для пользователя {}: {}", userId, e.getMessage());
      throw e;
    }
  }

  @Transactional
  public void deleteAllUserSchedules(Long userId) {
    try {
      scheduleRepository.deleteByUserId(userId);
      log.info("Удалены все расписания для пользователя {}", userId);
    } catch (Exception e) {
      log.error("Ошибка при удалении расписаний для пользователя {}: {}", userId, e.getMessage());
      throw e;
    }
  }
}
