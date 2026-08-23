package com.safelogj.mikread.helpers;

import android.graphics.drawable.Drawable;
import android.text.method.HideReturnsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import com.safelogj.mikread.R;

public class PassFieldListener implements View.OnTouchListener {
    private boolean isPasswordVisible = false;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (!(v instanceof EditText editText)) return false;

        final int DRAWABLE_END = 2;
        if (event.getAction() == MotionEvent.ACTION_UP) {
            Drawable drawableEnd = editText.getCompoundDrawables()[DRAWABLE_END];
            if (drawableEnd != null) {
                float x = event.getX();
                int width = editText.getWidth();
                int paddingEnd = editText.getPaddingEnd();
                int drawableWidth = drawableEnd.getBounds().width();

                if (x >= (width - paddingEnd - drawableWidth)) {
                    // 1. Принудительно просим фокус для этого поля,
                    // чтобы курсор в другом поле исчез корректно
                    editText.requestFocus();

                    isPasswordVisible = !isPasswordVisible;

                    if (isPasswordVisible) {
                        editText.setTransformationMethod(HideReturnsTransformationMethod.getInstance());
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.visibility_20px, 0);
                    } else {
                        editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                        editText.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, R.drawable.visibility_off_20px, 0);
                    }

                    // 2. Ставим курсор в конец (теперь, когда фокус точно здесь, это безопасно)
                    editText.setSelection(editText.getText().length());

                    // 3. Сообщаем системе, что мы нажали на иконку
                    v.performClick();

                    return true; // Здесь оставляем true, так как по иконке мы попали
                }
            }
        }
        return false;
    }

}


