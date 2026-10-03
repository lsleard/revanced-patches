package app.revanced.extension.samsungkeyboard;

import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;

import java.lang.reflect.Method;

/**
 * Samsung Keyboard decides between its phone and tablet UX by asking the
 * package manager for the Samsung only feature
 * {@code com.samsung.feature.device_category_tablet}. Non-Samsung tablets never
 * declare it, so the keyboard falls back to the phone layout and loses tablet
 * only features such as the split keyboard in portrait.
 * <p>
 * The patch redirects those {@link PackageManager#hasSystemFeature(String)} calls
 * here. {@link #configuredCategory()} is rewritten by the patch with the chosen
 * option value.
 */
@SuppressWarnings("unused")
public final class DeviceCategoryCompat {
    private static final String TABLET_FEATURE = "com.samsung.feature.device_category_tablet";
    private static final String CATEGORY_AUTO = "Auto";
    private static final String CATEGORY_TABLET = "Tablet";
    private static final String CATEGORY_PHONE = "Phone";
    private static final int TABLET_SMALLEST_WIDTH_DP = 600;

    private static volatile Boolean tablet;

    private DeviceCategoryCompat() {
    }

    /**
     * Replaced by the patch with {@code const-string} of the selected option.
     */
    public static String configuredCategory() {
        String category = CATEGORY_AUTO;
        return category;
    }

    public static boolean hasSystemFeature(PackageManager packageManager, String name) {
        if (TABLET_FEATURE.equals(name)) return isTablet();
        return packageManager.hasSystemFeature(name);
    }

    private static boolean isTablet() {
        Boolean cached = tablet;
        if (cached != null) return cached;

        boolean value;
        String category = configuredCategory();
        if (CATEGORY_TABLET.equals(category)) {
            value = true;
        } else if (CATEGORY_PHONE.equals(category)) {
            value = false;
        } else {
            value = detectTablet();
        }
        tablet = value;
        return value;
    }

    private static boolean detectTablet() {
        try {
            Configuration configuration = Resources.getSystem().getConfiguration();
            if (configuration.smallestScreenWidthDp >= TABLET_SMALLEST_WIDTH_DP) return true;
        } catch (Throwable ignored) {
        }
        String characteristics = systemProperty("ro.build.characteristics");
        return characteristics != null && characteristics.contains("tablet");
    }

    private static String systemProperty(String key) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method get = systemProperties.getMethod("get", String.class);
            Object value = get.invoke(null, key);
            return value instanceof String ? (String) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }
}
