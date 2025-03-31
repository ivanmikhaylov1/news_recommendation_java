package com.example.demo.parser;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@AllArgsConstructor
public enum DefaultWebsiteIds {
  INFOQ(2),
  THREE_D(3),
  HI_TECH(1);

  @Getter
  private final long id;
}
