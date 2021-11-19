package com.overshade.cam.WidgetAnimations;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;

public class ButtonScaleAnimation {
    private final View           view;
    private       ScaleAnimation scaleAnim;
    private       ScaleAnimation unscaleAnim;
    private final int            durationScale;
    private       int            durationUnscale;


    /* Builder */

    public ButtonScaleAnimation(View v, int scaleTime, int unscaleTime) {
        view = v;
        durationScale = scaleTime;
        durationUnscale = unscaleTime;
        scaleAnim = (ScaleAnimation) scale();
        unscaleAnim = (ScaleAnimation) unscale();

    }

    public void start() {
        view.startAnimation(scaleAnim);
    }


    /* Scale animations works */

    private Animation scale() {
        scaleAnim = new ScaleAnimation(1f, 1.2f, 1f, 1.2f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        scaleAnim.setDuration(durationScale);
        scaleAnim.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                unscale();
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
        return scaleAnim;
    }

    private Animation unscale() {
        unscaleAnim = new ScaleAnimation(1.2f, 1f, 1.2f, 1f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        unscaleAnim.setDuration(durationUnscale);
        view.startAnimation(unscaleAnim);
        return unscaleAnim;
    }

}
