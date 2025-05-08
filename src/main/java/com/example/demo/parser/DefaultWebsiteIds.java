package com.example.demo.parser;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum DefaultWebsiteIds {
    HI_TECH("Hi-Tech"),
    INFOQ("Infoq"),
    THREE_D("3Dnews");

    private final String name;
}
