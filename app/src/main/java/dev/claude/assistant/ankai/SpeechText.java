package dev.claude.assistant.ankai;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Speech-only projection. Never use this to replace displayed message text. */
public final class SpeechText {
    private static final Pattern URL = Pattern.compile("(?i)https?://[^\\s<>\\\"]+");
    private static final Pattern LINK = Pattern.compile("\\[[^\\]\\n]*\\]\\((https?://(?:[^\\s()]|\\([^\\s()]*\\))+)\\)", Pattern.CASE_INSENSITIVE);
    private SpeechText() {}

    public static String forPlayback(String text) {
        if (text == null) return "";
        text = text.replaceAll("(?i)<(https?://[^\\s<>]+)>", "$1");
        Matcher links = LINK.matcher(text);
        StringBuffer plain = new StringBuffer();
        while (links.find()) links.appendReplacement(plain, Matcher.quoteReplacement(host(links.group(1))));
        links.appendTail(plain);
        Matcher urls = URL.matcher(plain);
        StringBuffer result = new StringBuffer();
        while (urls.find()) {
            String candidate = urls.group();
            int end = candidate.length();
            while (end > 0 && ".,;:!?)}".indexOf(candidate.charAt(end - 1)) >= 0) end--;
            String host = host(candidate.substring(0, end));
            urls.appendReplacement(result, Matcher.quoteReplacement(host + candidate.substring(end)));
        }
        urls.appendTail(result);
        return result.toString().trim();
    }

    private static String host(String url) {
        try {
            // Parse only the authority: malformed/unfinished path escapes must not leak a path.
            String authority = url.substring(url.indexOf("://") + 3).split("[/ ?#]", 2)[0];
            URI parsed = URI.create("https://" + authority);
            String host = parsed.getHost();
            if (host != null) return host;
            // Unicode DNS names are valid for speech even though URI.getHost rejects them.
            authority = authority.substring(authority.lastIndexOf('@') + 1);
            return authority.replaceFirst(":\\d+$", "");
        } catch (IllegalArgumentException e) { return ""; }
    }

    /** Keep a trailing URL/token or unfinished Markdown link until its boundary is known. */
    static int stableEnd(String text) {
        int end = text.length();
        int bracket = text.lastIndexOf('[');
        if (bracket >= 0 && text.indexOf(')', bracket) < 0) end = bracket;
        int token = text.length();
        while (token > 0 && !Character.isWhitespace(text.charAt(token - 1))) token--;
        String tail = text.substring(token).toLowerCase(java.util.Locale.ROOT);
        if (!tail.isEmpty() && ("https://".startsWith(tail) || "http://".startsWith(tail)
                || tail.contains("http://") || tail.contains("https://"))) end = Math.min(end, bracket >= 0 && bracket < token ? bracket : token);
        return end;
    }
}
