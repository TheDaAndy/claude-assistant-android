package dev.claude.assistant.ankai;

import java.util.Objects;

public class Assert {
    public static void eq(Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError("erwartet <" + expected + "> aber war <" + actual + ">");
        }
    }

    public static void isTrue(String message, boolean condition) {
        if (!condition) throw new AssertionError(message);
    }

    public static void fail(String message) {
        throw new AssertionError(message);
    }
}
