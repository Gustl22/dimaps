package org.oscim.app.preferences;

import android.content.Context;
import android.os.Build;
import android.preference.ListPreference;
import android.support.annotation.RequiresApi;
import android.util.AttributeSet;

/**
 * Created by gustl on 23.03.17.
 */

public class StoragePreference extends ListPreference {
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public StoragePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public StoragePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPreference(context, attrs);
    }

    private void initPreference(Context context, AttributeSet attrs) {
//        setValuesFromXml(attrs);
//        mSeekBar = new SeekBar(context, attrs);
//        mSeekBar.setMax(mMaxValue - mMinValue);
//        mSeekBar.setOnSeekBarChangeListener(this);
    }
}
