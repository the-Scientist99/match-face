package com.sportsapp.common;

import java.text.Collator;
import java.util.Comparator;
import java.util.Locale;

public final class Names {

  private Names() {}

  /**
   * The order for lists of names: Montenegrin alphabetical order (C Č Ć D Dž Đ …, the Gaj Latin alphabet the
   * JDK ships as Croatian), ignoring case. String.CASE_INSENSITIVE_ORDER and MySQL ORDER BY don't follow it,
   * so lists of names are sorted in Java.
   */
  public static final Comparator<String> ORDER = order();

  private static Comparator<String> order() {
    Collator c = Collator.getInstance(Locale.forLanguageTag("hr")); // RuleBasedCollator.compare is synchronized, so sharing is safe
    c.setStrength(Collator.SECONDARY);
    return c::compare;
  }
}
