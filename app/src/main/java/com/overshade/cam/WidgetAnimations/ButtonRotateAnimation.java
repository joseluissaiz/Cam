package com.overshade.cam.WidgetAnimations;

import android.view.View;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;

public class ButtonRotateAnimation {
    private final View            view;
    private final RotateAnimation rotateAnim;
    private final float           rotationF;
    private final int             duration;

    public ButtonRotateAnimation(View v, float rotation, int timeDuration) {
        view = v;
        rotationF = rotation;
        duration = timeDuration;
        rotateAnim = (RotateAnimation) rotate();
    }

    public ButtonRotateAnimation start() {
        view.startAnimation(rotateAnim);
        return this;
    }

    private Animation rotate() {
        RotateAnimation rotate = new RotateAnimation(
                view.getRotation(), rotationF,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f,
                ScaleAnimation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(duration);
        return rotate;
    }


}
