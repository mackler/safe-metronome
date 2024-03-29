package android.widget;

/* This code by Paul Tsupikoff
   with modifications by Fatal1ty2787 and Ramesh

   http://stackoverflow.com/questions/4892179/how-can-i-get-a-working-vertical-seekbar-in-android
 */

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class VerticalSeekBar extends SeekBar {

    private OnSeekBarChangeListener myListener;

    public VerticalSeekBar(Context context) {
        super(context);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public VerticalSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    private int x,y,z,w;
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(h, w, oldh, oldw);
        this.x=w;
        this.y=h;
        this.z=oldw;
        this.w=oldh;
    }

    @Override
    public synchronized void setProgress(int progress) {
	super.setProgress(progress);
	onSizeChanged(x, y, z, w);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec);
        setMeasuredDimension(getMeasuredHeight(), getMeasuredWidth());
    }

    @Override
    public void setOnSeekBarChangeListener(OnSeekBarChangeListener mListener){
	this.myListener = mListener;
    }

    protected void onDraw(Canvas c) {
        c.rotate(90);
	c.translate(0, -getWidth());
        super.onDraw(c);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
	if (!isEnabled()) {
	    return false;
	}

	switch (event.getAction()) {
        case MotionEvent.ACTION_DOWN:
            if(myListener!=null)
                myListener.onStartTrackingTouch(this);
            break;
        case MotionEvent.ACTION_MOVE:
            setProgress((int) (getMax() * event.getY() / getHeight()));
            onSizeChanged(getWidth(), getHeight(), 0, 0);
            myListener.onProgressChanged(this, (int) (getMax() * event.getY() / getHeight()), true);
            break;
        case MotionEvent.ACTION_UP:
            myListener.onStopTrackingTouch(this);
            break;

        case MotionEvent.ACTION_CANCEL:
            break;
	}
	return true;
    }
}
