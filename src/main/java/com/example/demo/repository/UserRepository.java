package com.example.demo.repository;


import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = """
            SELECT 
                COALESCE(MAX(u.lastSubmittedArticleId), 0)
            FROM 
                User u
            WHERE 
                u.lastSubmittedArticleId IS NOT NULL
                AND SIZE(u.categories) > 0
                AND SIZE(u.websites) > 0
            """)
    Long getMaxLastSubmittedArticleId();

    @Query(value = """
                SELECT u.*
                FROM users u
                JOIN user_websites uw ON u.user_id = uw.user_id
                WHERE uw.website_id = :#{#article.website.id}
                  AND (u.last_submitted_article_id IS NULL OR u.last_submitted_article_id < :#{#article.id})
                  AND u.user_id IN (
                      SELECT DISTINCT uc.user_id
                      FROM user_categories uc
                      JOIN article_category ac ON uc.category_id = ac.category_id
                      WHERE ac.article_id = :#{#article.id}
                  )
            """, nativeQuery = true)
    List<User> findUsersForArticle(@Param("article") Article article);
}

