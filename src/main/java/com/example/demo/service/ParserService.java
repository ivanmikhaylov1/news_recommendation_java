package com.example.demo.service;

import com.example.demo.parser.sites.HiTechParser;
import com.example.demo.parser.sites.InfoqParser;
import com.example.demo.parser.sites.RSSParser;
import com.example.demo.parser.sites.ThreeDNewsParser;
import com.example.demo.repository.ArticlesRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ParserService {
  private final ArticlesRepository articlesRepository;

  private final HiTechParser hiTechParser;
  private final InfoqParser infoqParser;
  private final ThreeDNewsParser threeDNewsParser;

  private final RSSParser rssParser;


}

