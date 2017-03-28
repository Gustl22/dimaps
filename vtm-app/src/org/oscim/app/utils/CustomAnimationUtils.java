package org.oscim.app.utils;

import android.content.Context;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Transformation;

/**
 * Created by Gustl on 09.03.2017.
 */

@RequiresApi(api = Build.VERSION_CODES.HONEYCOMB_MR1)
public class CustomAnimationUtils {

    public static void SlideUp(View view, Context context)
    {
        view.animate().translationY(-2 * view.getHeight());
//      view.setVisibility(View.GONE);
    }


    public static void SlideDown(View view,Context context)
    {
        view.animate().translationY(2 * view.getHeight());
//      view.setVisibility(View.GONE);
    }

    public static void SlideRight(View view,Context context)
    {
        view.animate().translationX(2 * view.getHeight());
//      view.setVisibility(View.GONE);
    }

    public static void SlideLeft(View view,Context context)
    {
        view.animate().translationX(-2 * view.getHeight());
//      view.setVisibility(View.GONE);
    }

    public static void SlideYBack(View view, Context context)
    {
        view.animate().translationY(0);
        //      view.setVisibility(View.VISIBLE);
    }

    public static void SlideXBack(View view, Context context)
    {
        view.animate().translationX(0);
//      view.setVisibility(View.VISIBLE);
    }


    static int initialHeight = 0;
    public static void expand(final View v) {
        v.measure(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        //final int targetHeight = v.getMeasuredHeight();

        // Older versions of android (pre API 21) cancel animations for views with a height of 0.
        v.getLayoutParams().height = 1;
        v.setVisibility(View.VISIBLE);
        Animation a = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                v.getLayoutParams().height = (int)(initialHeight * interpolatedTime);
                v.requestLayout();
                if(interpolatedTime == 1){
                    v.getLayoutParams().height = ViewGroup.LayoutParams.WRAP_CONTENT;
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration(150);
        v.startAnimation(a);
    }

    public static void collapse(final View v) {
        initialHeight = v.getMeasuredHeight();
        Animation a = new Animation()
        {
            @Override
            protected void applyTransformation(float interpolatedTime, Transformation t) {
                if(interpolatedTime == 1){
                    v.setVisibility(View.GONE);
                } else{
                    v.getLayoutParams().height = initialHeight - (int)(initialHeight * interpolatedTime);
                    v.requestLayout();
                }
            }

            @Override
            public boolean willChangeBounds() {
                return true;
            }
        };

        // 1dp/ms
        a.setDuration(150);
        v.startAnimation(a);
    }

}
