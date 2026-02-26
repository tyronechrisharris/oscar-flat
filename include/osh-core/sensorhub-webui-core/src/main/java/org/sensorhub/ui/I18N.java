package org.sensorhub.ui;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.MissingResourceException;
import com.vaadin.server.VaadinSession;
import java.text.MessageFormat;

public class I18N {
    private static final String BUNDLE_NAME = "org.sensorhub.ui.i18n.messages";

    public static String get(String key) {
        Locale locale = null;
        if (VaadinSession.getCurrent() != null) {
            locale = VaadinSession.getCurrent().getLocale();
        }

        if (locale == null) {
            locale = Locale.ENGLISH;
        }

        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
            String value = bundle.getString(key);
            // Always format to handle escaped quotes (e.g., '' -> ')
            return MessageFormat.format(value, new Object[0]);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        // We cannot call get(key) here because it would format once, and then format again below
        // which might break if the first format produced something that looks like a pattern.
        // Instead, retrieve raw string and format once.
        Locale locale = null;
        if (VaadinSession.getCurrent() != null) {
            locale = VaadinSession.getCurrent().getLocale();
        }

        if (locale == null) {
            locale = Locale.ENGLISH;
        }

        try {
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE_NAME, locale);
            String value = bundle.getString(key);
            return MessageFormat.format(value, args);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }
}
