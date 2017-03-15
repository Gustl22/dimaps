package org.oscim.core;

/**
 * Created by gustl on 13.03.17.
 */

public class S3DBTag extends Tag{
    public static final String KEY_COLOR = "c";
    public static final String KEY_MATERIAL = "m";
    public static final String KEY_ROOF = "roof";
    public static final String KEY_ROOF_SHAPE = "roof:shape";

    /**
     * @param key   the key of the tag.
     * @param value the value of the tag.
     */
    public S3DBTag(String key, String value) {
        super(key, value);
    }

    /**
     * Create Tag with interned Key.
     *
     * @param key         the key of the tag.
     * @param value       the value of the tag.
     * @param internValue true when value string should be intern()alized.
     */
    public S3DBTag(String key, String value, boolean internValue) {
        super(key, value, internValue);
    }

    public S3DBTag(String key, String value, boolean internKey, boolean internValue) {
        super(key, value, internKey, internValue);
    }
}
