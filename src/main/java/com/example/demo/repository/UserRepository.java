package com.example.demo.repository;


import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    @Query("SELECT u.id FROM User u "
        + "JOIN LEFT u.categories c "
        + "JOIN LEFT u.websites w "
        + "WHERE c.id = :#{#article.category.id} "
        + "AND w.id = :#{#article.website.id}")
    List<Long> findUserIdsByArticle(@Param("article") Article article);
}
