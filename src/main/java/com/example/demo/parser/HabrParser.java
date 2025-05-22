package com.example.demo.parser;

import com.example.demo.domain.dto.ArticleDTO;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
public class HabrParser {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36";
    private static final int TIMEOUT = 20000;
    private static final String DOMAIN = "https://habr.com";
    private static final String BLOG_LINK = DOMAIN + "/ru/news";

    @Value("${parser.limit}")
    private Integer limitArticleCount;

    public List<ArticleDTO> parseLastArticles() {
        Optional<Document> page = getPage(BLOG_LINK);
        return page.map(this::parseLastArticles).orElse(List.of());
    }

    public List<ArticleDTO> parseLastArticles(Document page) {
        List<ArticleDTO> articles = new ArrayList<>();
        List<String> links = extractArticleLinks(page);

        for (String link : links) {
            Optional<ArticleDTO> article = parseArticle(link);
            article.ifPresent(articles::add);
            if (articles.size() >= limitArticleCount) {
                break;
            }
        }

        return articles;
    }

    private List<String> extractArticleLinks(Document page) {
        List<String> result = new ArrayList<>();
        for (Element a : page.select("h2.tm-title a")) {
            String href = a.attr("href");
            if (href.contains("news")) {
                result.add(DOMAIN + href);
            }
        }
        return result;
    }

    private Optional<ArticleDTO> parseArticle(String url) {
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT)
                    .get();

            String title = Optional.ofNullable(doc.selectFirst("h1"))
                    .map(Element::text)
                    .orElse("Без названия");

            String date = Optional.ofNullable(doc.selectFirst("time"))
                    .map(Element::text)
                    .orElse("Дата не указана");

            StringBuilder fullText = new StringBuilder();
            for (Element p : doc.select("p")) {
                String txt = p.text().trim();
                if (!txt.isEmpty()) {
                    fullText.append(txt).append(" ");
                }
            }

            return Optional.of(ArticleDTO.builder()
                    .name(title)
                    .description(fullText.toString().trim())
                    .url(url)
                    .date(date)
                    .build());

        } catch (Exception e) {
            log.error("Parsing error: {}", url, e);
            return Optional.empty();
        }
    }

    private Optional<Document> getPage(String link) {
        try {
            if (link == null || link.trim().isEmpty()) {
                return Optional.empty();
            }

            return Optional.ofNullable(Jsoup.connect(link)
                    .timeout(TIMEOUT)
                    .userAgent(USER_AGENT)
                    .get());
        } catch (Exception e) {
            log.error("Get request error: {}", link, e);
            return Optional.empty();
        }
    }
}
