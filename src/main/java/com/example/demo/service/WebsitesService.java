package com.example.demo.service;

import com.example.demo.domain.dto.WebsiteDTO;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Website;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebsitesService {

  private static final Logger log = LoggerFactory.getLogger(WebsitesService.class);
  private final WebsiteRepository repository;
  private final UserRepository userRepository;

  @Transactional(readOnly = true)
  public List<WebsiteDTO> getDefaultWebsites() {
    List<Website> websites = repository.findByOwnerIsNull();
    return websites.stream()
        .map(website -> new WebsiteDTO(website.getId(), website.getName(), website.getUrl()))
        .collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public List<WebsiteDTO> getUserWebsites(User user) {
    List<Website> websites = repository.findByUsersContaining(user);
    return websites.stream()
        .map(website -> new WebsiteDTO(website.getId(), website.getName(), website.getUrl()))
        .collect(Collectors.toList());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void chooseWebsite(User user, Long websiteId) {
    log.info("Добавление сайта с ID {} для пользователя с ID {}", websiteId, user.getId());

    Optional<Website> websiteOpt = repository.findById(websiteId);
    if (websiteOpt.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Website with this ID not found");
    }

    Website website = websiteOpt.get();
    User freshUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    if (freshUser.getWebsites() == null) {
      freshUser.setWebsites(new java.util.HashSet<>());
    }

    freshUser.getWebsites().add(website);
    userRepository.save(freshUser);
    log.info("Сайт с ID {} успешно добавлен пользователю с ID {}", websiteId, user.getId());
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void removeWebsite(User user, Long websiteId) {
    log.info("Удаление сайта с ID {} для пользователя с ID {}", websiteId, user.getId());

    Optional<Website> websiteOpt = repository.findByIdAndUsersContaining(websiteId, user);
    if (websiteOpt.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Website with this ID not found");
    }

    Website website = websiteOpt.get();
    User freshUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    if (website.getOwner() != null && website.getOwner().getId().equals(user.getId())) {
      repository.delete(website);
      log.info("Сайт с ID {} полностью удален из базы данных", websiteId);
    } else {
      freshUser.getWebsites().remove(website);
      userRepository.save(freshUser);
      log.info("Сайт с ID {} удален у пользователя с ID {}", websiteId, user.getId());
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public WebsiteDTO createWebsite(User user, Website website) {
    log.info("Создание нового сайта '{}' пользователем с ID {}", website.getName(), user.getId());
    User freshUser = userRepository.findById(user.getId())
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

    website.setOwner(freshUser);
    Website savedWebsite = repository.save(website);
    log.info("Сайт '{}' успешно создан с ID {}", website.getName(), savedWebsite.getId());

    return new WebsiteDTO(savedWebsite.getId(), savedWebsite.getName(), savedWebsite.getUrl());
  }
}
