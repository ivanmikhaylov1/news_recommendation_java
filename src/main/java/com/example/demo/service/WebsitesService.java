package com.example.demo.service;

import com.example.demo.domain.dto.WebsiteDTO;
import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Website;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WebsiteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebsitesService {

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

  @Transactional
  public void chooseWebsite(User user, Long websiteId) {
    Optional<Website> website = repository.findById(websiteId);
    if (website.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Website with this ID not found");
    }
    user.getWebsites().add(website.get());
    userRepository.save(user);
  }

  @Transactional
  public void removeWebsite(User user, Long websiteId) {
    Optional<Website> website = repository.findByIdAndUsersContaining(websiteId, user);
    if (website.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Website with this ID not found");
    }
    user.getWebsites().remove(website.get());
    userRepository.save(user);
  }

  @Transactional
  public WebsiteDTO createWebsite(User user, Website website) {
    website.setOwner(user);
    repository.save(website);
    return new WebsiteDTO(website.getId(), website.getName(), website.getUrl());
  }
}