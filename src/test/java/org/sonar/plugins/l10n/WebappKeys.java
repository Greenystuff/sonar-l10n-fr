/*
 * French Pack for SonarQube
 * Copyright (C) 2011-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.l10n;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dumps the message catalogue the SonarQube web app actually ships, read from a running server.
 *
 * <p>Usage: {@code tools/webapp-keys.sh [url]}, default {@code http://localhost:9020}.
 * Writes {@code target/webapp-keys.properties}.
 *
 * <p>Why this is needed: {@code org/sonar/l10n/core.properties} is labelled "LEGACY FILE" by
 * SonarSource and is no longer the catalogue the front end uses. The real one lives in the
 * web app bundle, and the two have diverged in both directions:
 * <ul>
 *   <li>keys the web app has and core.properties does not -- roughly three thousand of them,
 *       including the whole login screen. {@code DefaultI18n} indexes only what the English
 *       bundle declares, so adding those to {@code core_fr.properties} would have no effect
 *       whatsoever: they are served through the plugin's own bundle instead;</li>
 *   <li>keys both have, with different argument names. The French file follows
 *       core.properties because that is what the harness compares against, so at runtime it
 *       hands react-intl an argument the component never passes, and the placeholder is
 *       rendered literally on screen.</li>
 * </ul>
 *
 * <p>Both are only visible by looking at what the server serves, which is what this does.
 * There is no published artifact to read instead: the generator SonarSource mentions in the
 * header of core.properties lives in a private part of their front-end repository.
 */
public final class WebappKeys {

  private static final Path REPORT = Paths.get("target/webapp-keys.properties");

  /** Script tags in index.html, which is where the bundle names are. */
  private static final Pattern SCRIPT = Pattern.compile("src=\"([^\"]+\\.js)\"");

  /**
   * A quoted key mapped to a quoted string. The key must contain a dot: bare identifiers in
   * minified code are overwhelmingly variable names, not message keys, and the few real ones
   * are recovered by {@link #BARE_ENTRY}.
   */
  private static final Pattern QUOTED_ENTRY =
    Pattern.compile("\"([a-z][A-Za-z0-9_]*(?:\\.[A-Za-z0-9_\\-]+)+)\":\"((?:[^\"\\\\]|\\\\.)*)\"");

  /** Unquoted key mapped to a quoted string, for the single-word keys such as {@code username}. */
  private static final Pattern BARE_ENTRY =
    Pattern.compile("[{,]([a-z][a-z0-9_]{2,}):\"((?:[^\"\\\\]|\\\\.)*)\"");

  private WebappKeys() {
    // utility class
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    String base = args.length > 0 ? args[0] : "http://localhost:9020";
    HttpClient client = HttpClient.newHttpClient();

    String index = get(client, base + "/");
    Set<String> scripts = new LinkedHashSet<>();
    Matcher matcher = SCRIPT.matcher(index);
    while (matcher.find()) {
      scripts.add(matcher.group(1));
    }
    if (scripts.isEmpty()) {
      throw new IllegalStateException("No script tag found at " + base + " -- is the server up?");
    }

    TreeMap<String, String> messages = new TreeMap<>();
    for (String script : scripts) {
      String body = get(client, script.startsWith("http") ? script : base + script);
      collect(QUOTED_ENTRY, body, messages, true);
      collect(BARE_ENTRY, body, messages, false);
    }

    StringBuilder out = new StringBuilder();
    messages.forEach((key, value) -> out.append(key).append('=').append(value).append('\n'));
    Files.createDirectories(REPORT.getParent());
    Files.write(REPORT, out.toString().getBytes(StandardCharsets.UTF_8));

    System.out.printf("%d messages read from %d bundle(s) at %s%n", messages.size(), scripts.size(), base);
    System.out.println("Written to " + REPORT);
  }

  private static void collect(Pattern pattern, String body, TreeMap<String, String> into, boolean overwrite) {
    Matcher matcher = pattern.matcher(body);
    while (matcher.find()) {
      String key = matcher.group(1);
      if (overwrite || !into.containsKey(key)) {
        into.put(key, unescapeJs(matcher.group(2)));
      }
    }
  }

  /** Undoes the escaping the JavaScript string literal carries, so values compare as text. */
  private static String unescapeJs(String value) {
    StringBuilder out = new StringBuilder(value.length());
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c != '\\' || i + 1 >= value.length()) {
        out.append(c);
        continue;
      }
      char next = value.charAt(++i);
      switch (next) {
        case 'n' -> out.append('\n');
        case 't' -> out.append('\t');
        case 'r' -> out.append('\r');
        case 'u' -> {
          if (i + 4 < value.length()) {
            out.append((char) Integer.parseInt(value.substring(i + 1, i + 5), 16));
            i += 4;
          }
        }
        default -> out.append(next);
      }
    }
    return out.toString();
  }

  private static String get(HttpClient client, String url) throws IOException, InterruptedException {
    HttpResponse<String> response = client.send(
      HttpRequest.newBuilder(URI.create(url)).GET().build(),
      HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200) {
      throw new IllegalStateException("HTTP " + response.statusCode() + " for " + url);
    }
    return response.body();
  }
}
