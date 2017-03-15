package org.oscim.app;

import android.content.Context;
import android.view.View;
import android.view.animation.AnimationUtils;

/**
 * Created by Gustl on 09.03.2017.
 */

public class CustomAnimationUtils {

    public static void SlideUp(View view, Context context)
    {
        view.startAnimation(AnimationUtils.loadAnimation(context,
                R.anim.slide_up));
        view.setVisibility(View.GONE);
    }


    public static void SlideDown(View view,Context context)
    {
        view.startAnimation(AnimationUtils.loadAnimation(context,
                R.anim.slide_down));
        view.setVisibility(View.GONE);
    }

    public static void SlideRight(View view,Context context)
    {
        view.startAnimation(AnimationUtils.loadAnimation(context,
                R.anim.slide_right));
        view.setVisibility(View.GONE);
    }

    public static void SlideLeft(View view,Context context)
    {
        view.startAnimation(AnimationUtils.loadAnimation(context,
                R.anim.slide_left));
        view.setVisibility(View.GONE);
    }

    public static void SlideUpBack(View view, Context context)
    {
        view.startAnimation(AnimationUtils.loadAnimation(context,
                R.anim.slide_up_back));
        view.setVisibility(View.VISIBLE);
    }


    public static void SlideDownBack(View view,Context context)
    {
        view.startAnimation(AnimationUtils.loadAnimation(context,
                R.anim.slide_down_back));
        view.setVisibility(View.VISIBLE);
    }

    public static void SlideRightBack(View view,Context context)
    {
        view.startAnimation(AnimationUtils.loadAnimation(context,
                R.anim.slide_right_back));
        view.setVisibility(View.VISIBLE);
    }

    public static void SlideLeftBack(View view,Context context)
    {
        view.startAnimation(AnimationUtils.loadAnimation(context,
                R.anim.slide_left_back));
        view.setVisibility(View.VISIBLE);
    }


}
