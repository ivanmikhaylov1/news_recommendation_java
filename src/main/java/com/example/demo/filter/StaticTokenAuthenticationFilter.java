package com.example.demo.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class StaticTokenAuthenticationFilter extends OncePerRequestFilter {

  private static final String STATIC_TOKEN_HEADER = "X-Internal-Auth";

  @Value("${bot.internalToken}")
  private String staticToken;

  @Override
  protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getRequestURI().startsWith("/internal/")) {
      String token = request.getHeader(STATIC_TOKEN_HEADER);

      if (token.equals(staticToken)) {
        filterChain.doFilter(request, response);
        return;
      } else {
        response.sendError(HttpServletResponse.SC_FORBIDDEN, "Forbidden: Invalid static token");
        return;
      }
    }
    filterChain.doFilter(request, response);
  }
}