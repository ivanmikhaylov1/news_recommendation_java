package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "user_reactions")
public class UserReaction {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_reaction_id_seq")
  @SequenceGenerator(name = "user_reaction_id_seq", sequenceName = "user_reaction_id_seq", allocationSize = 1)
  @Column(name = "user_reaction_id")
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "article_id", nullable = false)
  private Article article;

  @Enumerated(EnumType.STRING)
  @Column(name = "reaction_type", nullable = false)
  private ReactionType reactionType;

  @Column(name = "created_at")
  private LocalDateTime createdAt;

  public enum ReactionType {
    LIKE,
    DISLIKE
  }
}
