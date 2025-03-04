package com.example.demo.domain.model;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "article_reactions")
public class ArticleReaction {

  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "article_reaction_id_seq")
  @SequenceGenerator(name = "article_reaction_id_seq", sequenceName = "article_reaction_id_seq", allocationSize = 1)
  @Column(name = "article_reaction_id")
  private Long id;

  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "article_id", nullable = false)
  private Article article;

  @Column(name = "likes_count")
  private Integer likesCount = 0;

  @Column(name = "dislikes_count")
  private Integer dislikesCount = 0;

  @Column(name = "rating")
  private Float rating = 0.0f;
}