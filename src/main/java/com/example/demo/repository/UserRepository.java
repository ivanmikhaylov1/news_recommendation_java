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
                COALESCE(MIN(u.lastSubmittedArticleId), 0)
            FROM 
                User u
            WHERE 
                u.lastSubmittedArticleId IS NOT NULL
                AND SIZE(u.categories) > 0
                AND SIZE(u.websites) > 0
            """)
    Long getMinLastSubmittedArticleId();

    @Query("""
            SELECT DISTINCT u
            FROM User u
            JOIN u.categories uc
            JOIN Article a
            JOIN a.categories ac
            WHERE 
                a = :article
                AND (u.lastSubmittedArticleId IS NULL OR u.lastSubmittedArticleId <= a.id)
                AND a.website MEMBER OF u.websites
                AND ac IN ELEMENTS(u.categories)
                AND (
                    u.notificationSchedules IS EMPTY OR
                    EXISTS (
                        SELECT 1
                        FROM NotificationSchedule ns
                        WHERE ns.user = u
                          AND ns.isActive = TRUE
                          AND :hour BETWEEN ns.startHour AND ns.endHour
                    )
                )
            """)
    List<User> findUsersForArticle(@Param("article") Article article, @Param("hour") Integer hour);
}

