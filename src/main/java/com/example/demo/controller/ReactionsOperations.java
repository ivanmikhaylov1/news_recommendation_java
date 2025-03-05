package com.example.demo.controller;

import com.example.demo.domain.model.Article;
import com.example.demo.domain.model.ArticleReaction;
import com.example.demo.domain.model.UserReaction;

import java.util.List;
import java.util.Optional;

public interface ReactionsOperations {
  ArticleReaction findByArticleId(); //TODO

  List<Article> findTop10(); //TODO

  Optional<UserReaction> findByUserId(); //TODO

  long countRating(); //TODO
}
