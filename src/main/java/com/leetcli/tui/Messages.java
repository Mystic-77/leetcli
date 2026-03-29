package com.leetcli.tui;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Centralised user-facing text loaded from resource bundles.
 * Supports internationalisation through standard Java ResourceBundle.
 */
public final class Messages {

    private static final ResourceBundle bundle;

    static {
        bundle = ResourceBundle.getBundle("messages", Locale.getDefault());
    }

    private Messages() {}

    public static String get(String key) {
        try { return bundle.getString(key); }
        catch (Exception e) { return key; }
    }

    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key), args);
    }
}
