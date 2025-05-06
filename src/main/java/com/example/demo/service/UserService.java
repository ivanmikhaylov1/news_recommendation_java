package com.example.demo.service;

import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.NotificationSchedule;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Website;
import com.example.demo.exception.EntityNotFoundException;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
  private final UserRepository userRepository;
  private final NotificationScheduleRepository scheduleRepository;
  private final WebsiteRepository websiteRepository;
  private final CategoryRepository categoryRepository;
  private final ArticlesRepository articlesRepository;

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
        schedule = scheduleRepository.save(schedule);
        user.getNotificationSchedules().add(schedule);
        user = userRepository.save(user);
      }

      return user;
    }

    User newUser = User.createUser(userId);
    try {
      User savedUser = userRepository.save(newUser);
      NotificationSchedule defaultSchedule = NotificationSchedule.createDefaultSchedule(savedUser);
      defaultSchedule = scheduleRepository.save(defaultSchedule);
      savedUser.getNotificationSchedules().add(defaultSchedule);
      savedUser = userRepository.save(savedUser);

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
        .orElseThrow(() -> new EntityNotFoundException("Пользователь с ID " + userId + " не найден"));

    
    user.getCategories().clear();
    user.getWebsites().clear();
    userRepository.save(user);
    scheduleRepository.deleteByUserId(userId);
    articlesRepository.deleteByWebsiteOwner(user);
    for (Category category : user.getOwnedCategories()) {
      category.setOwner(null);
    }

    categoryRepository.saveAll(user.getOwnedCategories());
    user.getOwnedCategories().clear();
    for (Website website : user.getOwnedWebsites()) {
      website.setOwner(null);
    }
    websiteRepository.saveAll(user.getOwnedWebsites());
    user.getOwnedWebsites().clear();
    userRepository.save(user);
    userRepository.deleteById(userId);

    log.info("Пользователь с ID {} удален", userId);
  }
}
