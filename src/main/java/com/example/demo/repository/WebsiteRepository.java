package com.example.demo.repository;

import com.example.demo.domain.model.User;
import com.example.demo.domain.model.Website;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface WebsiteRepository extends JpaRepository<Website, Long> {
  @Query("SELECT w FROM Website w WHERE w.owner IS NULL")
  List<Website> getDefaultWebsite();

  @Query("SELECT w FROM Website w JOIN w.users u WHERE u = :user")
  List<Website> getUserWebsite(User user);

  @Modifying
  @Transactional
  @Query(value = "INSERT INTO user_websites (website_id, user_id) VALUES (:websiteId, :userId)", nativeQuery = true)
  void chooseWebsite(@Param("userId") Long userId, @Param("websiteId") Long websiteId);

  @Modifying
  @Transactional
  @Query(value = "DELETE FROM user_websites WHERE website_id = :websiteId AND user_id = :userId", nativeQuery = true)
  void removeWebsite(@Param("userId") Long userId, @Param("websiteId") Long websiteId);
}
