package org.oscim.app.utils;

import android.graphics.Color;

/**
 * Created by Gustl on 09.03.2017.
 */

public class ColorUtils {
    public static int adjustAlpha(int color, float factor) {
        int alpha = Math.round(Color.alpha(color) * factor);
        int red = Color.red(color);
        int green = Color.green(color);
        int blue = Color.blue(color);
        return Color.argb(alpha, red, green, blue);
    }
}

