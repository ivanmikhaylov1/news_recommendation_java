package com.example.demo.parser;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DefaultWebsiteIds {
  INFOQ("Infoq"),
  THREE_D("3Dnews"),
  HI_TECH("Hi-Tech");

  private final String name;
}
