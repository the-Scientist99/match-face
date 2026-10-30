package com.sportsapp.common;

/**
 * Offset paging for the open lists, which hold only what's open right now and so stay short. {@code page} counts
 * from 0, and a page past the end is the last one, so a list that shrinks never shows an empty page.
 */
public record Paging(int page, int totalPages, int offset, int size) {

  private static final int SIZE = 9;

  public static Paging of(int total, int requestedPage) {
    if (requestedPage < 0) throw ApiException.badRequest("INVALID_FIELD");
    int totalPages = total / SIZE + (total % SIZE > 0 ? 1 : 0);
    int page = Math.min(requestedPage, Math.max(totalPages - 1, 0));
    return new Paging(page, totalPages, page * SIZE, SIZE);
  }

  /** A LIKE pattern for text containing {@code q}, whose own wildcards count as plain characters. */
  public static String contains(String q) {
    return "%" + q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
  }
}
