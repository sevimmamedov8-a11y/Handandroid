package com.handar.browser;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

public class HandOverlayView extends View {
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cursorPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<List<PointF>> hands = new ArrayList<>();
    private float cursorX = -1f;
    private float cursorY = -1f;
    private boolean pinching = false;

    private static final int[][] CONNECTIONS = new int[][]{
            {0,1},{1,2},{2,3},{3,4},
            {0,5},{5,6},{6,7},{7,8},
            {5,9},{9,10},{10,11},{11,12},
            {9,13},{13,14},{14,15},{15,16},
            {13,17},{17,18},{18,19},{19,20},
            {0,17}
    };

    public HandOverlayView(Context context) { super(context); init(); }
    public HandOverlayView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public HandOverlayView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        setWillNotDraw(false);
        linePaint.setStrokeWidth(3f);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setColor(0xB8FFFFFF);
        pointPaint.setStyle(Paint.Style.FILL);
        pointPaint.setColor(0xFFFFFFFF);
        cursorPaint.setStyle(Paint.Style.STROKE);
        cursorPaint.setStrokeWidth(4f);
        cursorPaint.setColor(0xFF4BA8FF);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public synchronized void setHands(List<List<PointF>> newHands) {
        hands.clear();
        hands.addAll(newHands);
        invalidate();
    }

    public synchronized void setCursor(float x, float y, boolean pinched) {
        cursorX = x;
        cursorY = y;
        pinching = pinched;
        invalidate();
    }

    public synchronized void clearAll() {
        hands.clear();
        cursorX = cursorY = -1f;
        pinching = false;
        invalidate();
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (List<PointF> hand : hands) {
            for (int[] c : CONNECTIONS) {
                if (c[0] < hand.size() && c[1] < hand.size()) {
                    PointF a = hand.get(c[0]);
                    PointF b = hand.get(c[1]);
                    canvas.drawLine(a.x, a.y, b.x, b.y, linePaint);
                }
            }
            for (PointF p : hand) canvas.drawCircle(p.x, p.y, 4f, pointPaint);
        }
        if (cursorX >= 0f && cursorY >= 0f) {
            cursorPaint.setColor(pinching ? 0xFFFFC533 : 0xFF4BA8FF);
            canvas.drawCircle(cursorX, cursorY, pinching ? 18f : 13f, cursorPaint);
            canvas.drawCircle(cursorX, cursorY, 4f, cursorPaint);
        }
    }
}
