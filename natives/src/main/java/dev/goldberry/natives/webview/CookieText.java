package dev.goldberry.natives.webview;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;

import dev.goldberry.log.Logs;

/// The text `goldberry_webview_cookies` answers with, read into
/// [HttpCookie]s.
///
/// One cookie per line, seven tab-separated fields:
///
/// ```text
/// name  value  domain  path  expires  secure  http-only
/// ```
///
/// `expires` is whole seconds since 1970, or `-1` for a session cookie; the
/// last two are `0` or `1`. A backslash, tab, newline or carriage return inside
/// a field arrives as `\\`, `\t`, `\n` or `\r`.
///
/// Text rather than an array of structs because three engines with three cookie
/// types answer in it, and a field added later is a column rather than a struct
/// layout that two languages must agree about.
final class CookieText {

    private static final Logger LOG = Logs.of(CookieText.class);

    /// How many fields a line has.
    private static final int FIELDS = 7;

    private CookieText() {}

    /// Reads every cookie in `text`.
    ///
    /// A line that is not seven fields, or whose name `HttpCookie` refuses — a
    /// reserved word such as `Path`, a name with a space in it — is skipped and
    /// logged rather than failing the whole answer: one odd cookie in a jar
    /// should not hide the session cookie beside it.
    ///
    /// @param text the shim's answer
    /// @param now  the current time, in seconds since 1970, which turns an
    ///        expiry date into the max-age `HttpCookie` keeps
    /// @return the cookies, in the engine's order
    static List<HttpCookie> parse(String text, long now) {
        Objects.requireNonNull(text, "text");
        var cookies = new ArrayList<HttpCookie>();
        for (var line : text.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            var fields = line.split("\t", -1);
            if (fields.length != FIELDS) {
                LOG.debug("a cookie line has {} fields rather than {}; skipped", fields.length, FIELDS);
                continue;
            }
            try {
                var cookie = new HttpCookie(unescape(fields[0]), unescape(fields[1]));
                // Version 0, the Netscape cookie every browser sends. The
                // constructor's default is RFC 2965's version 1, which quotes the
                // value when the cookie is written back into a header.
                cookie.setVersion(0);
                var domain = unescape(fields[2]);
                if (!domain.isEmpty()) {
                    cookie.setDomain(domain);
                }
                var path = unescape(fields[3]);
                if (!path.isEmpty()) {
                    cookie.setPath(path);
                }
                var expires = Long.parseLong(fields[4]);
                cookie.setMaxAge(expires < 0 ? -1 : Math.max(0, expires - now));
                cookie.setSecure("1".equals(fields[5]));
                cookie.setHttpOnly("1".equals(fields[6]));
                cookies.add(cookie);
            } catch (IllegalArgumentException e) {
                LOG.debug("a cookie the engine holds cannot be a java.net.HttpCookie ({}); skipped", e.getMessage());
            }
        }
        return List.copyOf(cookies);
    }

    /// Undoes the shim's four escapes. A backslash before anything else is
    /// kept as written.
    static String unescape(String field) {
        if (field.indexOf('\\') < 0) {
            return field;
        }
        var out = new StringBuilder(field.length());
        for (var index = 0; index < field.length(); index++) {
            var character = field.charAt(index);
            if (character != '\\' || index + 1 == field.length()) {
                out.append(character);
                continue;
            }
            var next = field.charAt(++index);
            switch (next) {
                case 't' -> out.append('\t');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case '\\' -> out.append('\\');
                default -> out.append('\\').append(next);
            }
        }
        return out.toString();
    }
}
