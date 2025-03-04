package com.example.demo.repository;

import com.example.demo.domain.model.Category;
import com.example.demo.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
  @Query("SELECT c FROM Category c WHERE c.owner IS NULL")
  List<Category> getDefaultCategories();

  @Query("SELECT c FROM Category c JOIN c.users u WHERE u = :user")
  List<Category> getUserCategories(User user);

  @Modifying
  @Transactional
  @Query(value = "INSERT INTO user_categories (category_id, user_id) VALUES (:categoryId, :userId)", nativeQuery = true)
  void chooseCategory(@Param("userId") Long userId, @Param("categoryId") Long categoryId);

  @Modifying
  @Transactional
  @Query(value = "DELETE FROM user_categories WHERE category_id = :categoryId AND user_id = :userId", nativeQuery = true)
  void removeCategory(@Param("userId") Long userId, @Param("categoryId") Long categoryId);
}
