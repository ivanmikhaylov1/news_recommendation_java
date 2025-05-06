package com.example.demo.exception;

public class ArticleClassificationException extends RuntimeException {
  public ArticleClassificationException(String message) {
    super(message);
  }

  public ArticleClassificationException(String message, Throwable cause) {
    super(message, cause);
  }
} 