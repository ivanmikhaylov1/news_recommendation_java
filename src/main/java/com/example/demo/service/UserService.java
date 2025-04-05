package com.example.demo.service;

import com.example.demo.domain.model.NotificationSchedule;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.Website;
import com.example.demo.repository.NotificationScheduleRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final NotificationScheduleRepository scheduleRepository;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public User getOrCreateUser(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("ID пользователя не может быть null");
    }

    Optional<User> existingUser = userRepository.findById(userId);
    if (existingUser.isPresent()) {
      User user = existingUser.get();
      NotificationSchedule schedule = user.getActiveSchedule();
      if (schedule == null) {
        schedule = NotificationSchedule.createDefaultSchedule(user);
        scheduleRepository.save(schedule);
      }

      return user;
    }

    User newUser = User.createUser(userId);
    try {
      User savedUser = userRepository.save(newUser);
      NotificationSchedule defaultSchedule = NotificationSchedule.createDefaultSchedule(savedUser);
      scheduleRepository.save(defaultSchedule);
      Optional<User> checkUser = userRepository.findById(userId);
      if (checkUser.isPresent()) {
        return savedUser;
      } else {
        throw new RuntimeException("Не удалось сохранить пользователя");
      }
    } catch (Exception e) {
      throw new RuntimeException("Ошибка при сохранении пользователя: " + e.getMessage(), e);
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void deleteUser(Long userId) {
    if (userId == null) {
      throw new IllegalArgumentException("ID пользователя не может быть null");
    }

    User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("Пользователь с ID " + userId + " не найден"));

    user.getCategories().clear();
    user.getWebsites().clear();
    for (Category category : user.getOwnedCategories()) {
      category.setOwner(null);
    }
    
    user.getOwnedCategories().clear();
    for (Website website : user.getOwnedWebsites()) {
      website.setOwner(null);
    }
    user.getOwnedWebsites().clear();
    userRepository.save(user);
    userRepository.deleteById(userId);
  }
}
