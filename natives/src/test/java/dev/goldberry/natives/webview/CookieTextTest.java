package dev.goldberry.natives.webview;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// The cookie text the shim answers with reads into `HttpCookie`s, HttpOnly
/// and all.
@DisplayName("the cookie text a page answers with")
class CookieTextTest {

    private static final long NOW = 1_800_000_000L;

    @Test
    @DisplayName("reads every field, the HttpOnly flag above all")
    void readsEveryField() {
        var cookies = CookieText.parse("grafana_session\tabc\t.example.org\t/\t1800003600\t1\t1\n", NOW);

        assertEquals(1, cookies.size());
        var cookie = cookies.getFirst();
        assertEquals("grafana_session", cookie.getName());
        assertEquals("abc", cookie.getValue());
        assertEquals(".example.org", cookie.getDomain());
        assertEquals("/", cookie.getPath());
        assertEquals(3600, cookie.getMaxAge());
        assertTrue(cookie.getSecure());
        assertTrue(cookie.isHttpOnly());
        assertEquals(0, cookie.getVersion(), "a browser's cookie is a Netscape one, written back unquoted");
        assertEquals("grafana_session=abc", cookie.toString());
    }

    @Test
    @DisplayName("keeps a session cookie a session cookie")
    void sessionCookie() {
        var cookie = CookieText.parse("a\tb\texample.org\t/\t-1\t0\t0\n", NOW).getFirst();

        assertEquals(-1, cookie.getMaxAge());
        assertFalse(cookie.getSecure());
        assertFalse(cookie.isHttpOnly());
    }

    @Test
    @DisplayName("undoes the four escapes and nothing else")
    void unescapes() {
        assertEquals("a\tb\nc\rd\\e", CookieText.unescape("a\\tb\\nc\\rd\\\\e"));
        assertEquals("\\x", CookieText.unescape("\\x"));
        assertEquals("plain", CookieText.unescape("plain"));
    }

    @Test
    @DisplayName("skips a line it cannot read and keeps the rest")
    void skipsTheOdd() {
        var cookies = CookieText.parse("broken\nbad name\tx\th\t/\t-1\t0\t0\nok\tv\th\t/\t-1\t0\t1\n", NOW);

        assertEquals(1, cookies.size(), "a short line and a name with a space are both skipped");
        assertEquals("ok", cookies.getFirst().getName());
    }

    @Test
    @DisplayName("reads an empty jar as no cookies")
    void empty() {
        assertTrue(CookieText.parse("", NOW).isEmpty());
    }
}
