package com.example.demo.domain.dto;

import com.example.demo.domain.model.NotificationSchedule;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationScheduleDTO {
  private Long id;
  private Long userId;
  private Integer startHour;
  private Integer endHour;
  private Boolean isActive;

  public static NotificationScheduleDTO fromEntity(NotificationSchedule schedule) {
    if (schedule == null) {
      return null;
    }

    return NotificationScheduleDTO.builder()
        .id(schedule.getId())
        .userId(schedule.getUser().getId())
        .startHour(schedule.getStartHour())
        .endHour(schedule.getEndHour())
        .isActive(schedule.getIsActive())
        .build();
  }
}
