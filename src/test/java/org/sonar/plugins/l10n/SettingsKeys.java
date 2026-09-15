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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Dumps the English labels of every settings definition a running SonarQube serves.
 *
 * <p>Usage: {@code tools/settings-keys.sh <url> <admin token>}.
 * Writes {@code target/settings-keys.properties}.
 *
 * <p>Why a second source next to {@link WebappKeys}: the administration screens do not read
 * their labels from the message catalogue. {@code /api/settings/list_definitions} hands the
 * front end a name and a description per setting, built in Java by each analyzer's
 * {@code PropertyDefinition}, and the front end shows those verbatim -- unless the catalogue
 * happens to carry {@code property.<key>.name}, which wins. Only a handful of core settings
 * have such a key today, which is why most of the administration is still in English however
 * complete {@code core_fr.properties} gets.
 *
 * <p>The mechanism is verifiable from outside: the API answers {@code Source File Exclusions}
 * for {@code sonar.exclusions}, and the screen shows the French of
 * {@code property.sonar.exclusions.name} instead.
 *
 * <p>Keys already declared by {@code core.properties} are skipped: redeclaring them in the
 * plugin bundle would shadow the core bundle for no gain, which
 * {@code FrenchPackExtensionBundleTest} rejects.
 *
 * <p>Write these descriptions as plain text, whatever the English does. The front end
 * registers exactly one rich-text element, {@code productName}, so react-intl reads any
 * other tag as one it has no handler for, fails to format, and falls back to the raw
 * string: {@code <br/>} and {@code <strong>} reach the screen as characters. Several
 * English descriptions carry markup and show it that way, so this is a rendering
 * limitation rather than a translation choice -- worth reporting upstream.
 *
 * <p>An admin token is required -- the endpoint is not public. The run is read-only.
 */
public final class SettingsKeys {

  private static final String REFERENCE_RESOURCE = "/org/sonar/l10n/core.properties";
  private static final Path REPORT = Paths.get("target/settings-keys.properties");

  private SettingsKeys() {
    // utility class
  }

  public static void main(String[] args) throws IOException, InterruptedException {
    if (args.length < 2) {
      throw new IllegalArgumentException("Usage: SettingsKeys <url> <admin token>");
    }
    String base = args[0];
    String token = args[1];

    Properties core = readReference();
    JsonObject payload = JsonParser.parseString(get(base, token)).getAsJsonObject();
    JsonArray definitions = payload.getAsJsonArray("definitions");

    TreeMap<String, String> messages = new TreeMap<>();
    TreeMap<String, String> categories = new TreeMap<>();
    for (JsonElement element : definitions) {
      JsonObject definition = element.getAsJsonObject();
      String key = text(definition, "key");
      if (key == null) {
        continue;
      }
      put(messages, core, "property." + key + ".name", text(definition, "name"));
      put(messages, core, "property." + key + ".description", text(definition, "description"));
      // Deliberately not the fields of a property set. The front end builds that table's
      // header straight from the definition -- {s.name}{s.description} -- with no
      // formatMessage in between, so no key can reach those two labels.
      collect(categories, text(definition, "category"), text(definition, "subCategory"));
    }

    // A category is its own English label when the analyzer that declares it supplies a display
    // name ("External Analyzers"); core categories are identifiers ("technicalDebt") and already
    // carry a key, so readReference filters those out.
    categories.forEach((suffix, label) -> put(messages, core, "property.category." + suffix, label));

    StringBuilder out = new StringBuilder();
    messages.forEach((key, value) ->
      out.append(escapeKey(key)).append('=').append(escape(value)).append('\n'));
    Files.createDirectories(REPORT.getParent());
    Files.write(REPORT, out.toString().getBytes(StandardCharsets.UTF_8));

    System.out.printf("%d definitions read from %s%n", definitions.size(), base);
    System.out.printf("%d keys core.properties does not already declare%n", messages.size());
    System.out.println("Written to " + REPORT);
  }

  /** Keys are {@code property.category.<category>[.<subCategory>]}, raw strings, no case folding. */
  private static void collect(TreeMap<String, String> into, String category, String subCategory) {
    if (category == null || category.isBlank()) {
      return;
    }
    into.put(category, category);
    if (subCategory != null && !subCategory.isBlank()) {
      into.put(category + "." + subCategory, subCategory);
    }
  }

  /** Keeps a key only when it carries text and core.properties does not already define it. */
  private static void put(TreeMap<String, String> into, Properties core, String key, String value) {
    if (value != null && !value.isBlank() && !core.containsKey(key)) {
      into.put(key, value);
    }
  }

  private static String text(JsonObject object, String member) {
    JsonElement element = object.get(member);
    return element == null || element.isJsonNull() ? null : element.getAsString();
  }

  /**
   * Category names carry spaces and punctuation -- "External Analyzers", "C#",
   * "JavaScript / TypeScript" -- which {@link Properties} would otherwise read as a separator
   * or a comment. Escaped here so the bundle round-trips.
   */
  private static String escapeKey(String key) {
    StringBuilder out = new StringBuilder(key.length());
    for (int i = 0; i < key.length(); i++) {
      char c = key.charAt(i);
      if (c == ' ' || c == '=' || c == ':' || (i == 0 && (c == '#' || c == '!'))) {
        out.append('\\');
      }
      out.append(c);
    }
    return out.toString();
  }

  /** Newlines would break the one-line-per-key format the bundles use. */
  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "");
  }

  private static Properties readReference() throws IOException {
    Properties properties = new Properties();
    try (InputStream stream = SettingsKeys.class.getResourceAsStream(REFERENCE_RESOURCE)) {
      if (stream == null) {
        throw new IllegalStateException(REFERENCE_RESOURCE + " not found -- is sonar-core on the classpath?");
      }
      properties.load(stream);
    }
    return properties;
  }

  private static String get(String base, String token) throws IOException, InterruptedException {
    HttpResponse<String> response = HttpClient.newHttpClient().send(
      HttpRequest.newBuilder(URI.create(base + "/api/settings/list_definitions"))
        .header("Authorization", "Bearer " + token)
        .GET().build(),
      HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() != 200) {
      throw new IllegalStateException("HTTP " + response.statusCode() + " for " + base
        + "/api/settings/list_definitions -- is the token an admin one?");
    }
    return response.body();
  }
}
