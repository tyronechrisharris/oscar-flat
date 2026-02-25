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
            return bundle.getString(key);
        } catch (MissingResourceException e) {
            return "!" + key + "!";
        }
    }

    public static String get(String key, Object... args) {
        String value = get(key);
        return MessageFormat.format(value, args);
    }
}
