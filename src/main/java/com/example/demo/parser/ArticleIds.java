package com.example.demo.parser;

public enum ArticleIds {
  INFOQ(1),
  THREE_D(2),
  HI_TECH(3);

  private final int id;

  ArticleIds(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }
}
