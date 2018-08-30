package com.asun.library;

import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

@SuppressLint("AppCompatCustomView")
public class ScalableImageView extends ImageView implements ViewTreeObserver.OnGlobalLayoutListener
        , ScaleGestureDetector.OnScaleGestureListener
        , GestureDetector.OnGestureListener
        , GestureDetector.OnDoubleTapListener
        , View.OnTouchListener {
    public static final String TAG = ScalableImageView.class.getSimpleName();
    private float mOverMaxScale;   // 最大回弹倍数
    private float mOverMinScale;   // 最小回弹倍数
    private float mMaxScale;    // 最大缩放倍数
    private final float[] mValues = new float[9];   // 保存图片变换信息
    private float mInitScale;  // 初始化图片的缩放倍数,也是双击手势的默认缩小倍数
    private ScaleGestureDetector mScaleGestureDetector;
    private GestureDetector mGestureDetector;
    private Matrix mMatrix;
    private boolean mHasInit;   // 记录view是否处于初次加载的状态
    private boolean mIsScaling; // 判断view是否处于双指缩放状态
    private float mLastX;   // 记录上一次事件的位置
    private float mLastY;   // 记录上一次时间的位置
    private float mPivotX;  // 记录上一次缩放中心点的x轴位置
    private float mPivotY;  // 记录上一次缩放中心点的y轴位置
    private float mDoubleTapScaleUp;    // 双击手势默认放大倍数
    private OnSingleTapListener mOnSingleTapListener = null;
    private OnLongPressListener mOnLongPressListener = null;

    public ScalableImageView(Context context) {
        this(context, null);
    }

    public ScalableImageView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public ScalableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        super.setScaleType(ScaleType.MATRIX);
        // 初始化工作
        mMatrix = new Matrix();
        mMatrix.reset();
        mHasInit = false;
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mGestureDetector = new GestureDetector(context, this);
        this.setOnTouchListener(this);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public ScalableImageView(Context context, @Nullable AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        super.setScaleType(ScaleType.MATRIX);
        // 初始化工作
        mMatrix = new Matrix();
        mMatrix.reset();
        mHasInit = false;
        mScaleGestureDetector = new ScaleGestureDetector(context, this);
        mGestureDetector = new GestureDetector(context, this);
        this.setOnTouchListener(this);

    }

    /**
     * view加载完毕后进行初始化工作
     */
    @Override
    public void onGlobalLayout() {
        // TODO: 添加类似微信图片查看器的进场退场动画逻辑
        // mMatrix = getImageMatrix();加入这句话后所有动作无法完成，原因未知
        if (!mHasInit) {
            mHasInit = true;

            final int width = getWidth();
            final int height = getHeight();
            final int drawableWidth = getDrawable().getIntrinsicWidth();
            final float scale = width / 1.0f / drawableWidth;   // 缩放比例，缩放后图片宽为控件宽

            mInitScale = scale;
            mMaxScale = mInitScale * 3.0f;
            mOverMaxScale = mMaxScale * 2.0f;
            mOverMinScale = mInitScale / 2.0f;
            mDoubleTapScaleUp = mInitScale * 1.7f;

            mMatrix.postScale(scale, scale);    // 默认缩放中心点为(0, 0),由于图片原始位置在左上角，故缩放后仍位于左上角
            RectF rectF = getMatrixRectF();
            if (rectF.height() < height) {
                mMatrix.postTranslate(0, (height - rectF.height()) / 2.0f);    // 若图片高度小于控件高度，。则将图片移动至居中位置
            }
            setImageMatrix(mMatrix);

        }

    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalLayoutListener(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            getViewTreeObserver().removeOnGlobalLayoutListener(this);
        } else {
            getViewTreeObserver().removeGlobalOnLayoutListener(this);
        }
    }

    @Override
    public boolean onScale(ScaleGestureDetector detector) {
        final float pivotX = detector.getFocusX();
        final float pivotY = detector.getFocusY();
        final int height = getHeight();
        float scale = detector.getScaleFactor();

        mPivotX = pivotX;
        mPivotY = pivotY;

        float currentScale = getCurrentScale();  // 获取当前缩放倍数
        if (scale * currentScale > mOverMaxScale) {
            scale = mOverMaxScale / currentScale;
        } else if (scale * currentScale < mOverMinScale) {
            scale = mOverMinScale / currentScale;
        }

        float currentHeight = getMatrixRectF().height();
        if (currentHeight < height) {
            mMatrix.postScale(scale, scale, mPivotX, height / 2.0f);   // 图片高度小于控件高度时y轴对称缩放时图片居中
        } else {
            mMatrix.postScale(scale, scale, pivotX, pivotY);
        }
        checkImageBounds();  // 由于不对称缩放，需要重新调整图片居中

        setImageMatrix(mMatrix);
        return true;
    }

    /**
     * 校正不对称缩放后的图片位置，以免图片与控件边缘间存在空隙
     */
    private void checkImageBounds() {
        final int width = getWidth();
        final int height = getHeight();
        RectF rectF = getMatrixRectF();
        if (rectF.width() <= width) {
            final float deltaX = (width - (rectF.right + rectF.left)) / 2.0f;
            mMatrix.postTranslate(deltaX, 0f);
        } else if (rectF.left > 0f) {
            mMatrix.postTranslate(-rectF.left, 0f);
        } else if (rectF.right < width) {
            mMatrix.postTranslate(width - rectF.right, 0f);
        }
        if (rectF.height() <= height) {
            final float deltaY = (height - (rectF.bottom + rectF.top)) / 2.0f;
            mMatrix.postTranslate(0f, deltaY);
        } else if (rectF.top > 0f) {
            mMatrix.postTranslate(0f, -rectF.top);
        } else if (rectF.bottom < height) {
            mMatrix.postTranslate(0f, height - rectF.bottom);
        }
    }

    /**
     * 获取当前缩放倍数
     *
     * @return
     */
    private float getCurrentScale() {
        mMatrix.getValues(mValues);
        return mValues[Matrix.MSCALE_X];
    }

    @Override
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        return true;
    }

    @Override
    public void onScaleEnd(ScaleGestureDetector detector) {
    }

    @Override
    public boolean onDown(MotionEvent e) {
        mLastX = e.getX();
        mLastY = e.getY();
        return true;
    }

    @Override
    public void onShowPress(MotionEvent e) {

    }

    @Override
    public boolean onSingleTapUp(MotionEvent e) {

        return true;
    }

    @Override
    public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
        final boolean canScrollHorizontally;
        final boolean canScrollVertically;

        RectF rectF = getMatrixRectF();

        float deltaX = e2.getX() - mLastX;
        float deltaY = e2.getY() - mLastY;
        mLastX = e2.getX();
        mLastY = e2.getY();

        if (deltaX > 0 && rectF.left < 0) { // 判断滑动是否超出范围
            deltaX = Math.min(-rectF.left, deltaX);
            canScrollHorizontally = true;
        } else if (deltaX < 0 && rectF.right > getWidth()) {
            deltaX = Math.max(-(rectF.right - getWidth()), deltaX);
            canScrollHorizontally = true;
        } else {
            canScrollHorizontally = false;
        }

        if (deltaY > 0 && rectF.top < 0) {  // 判断滑动是否超出范围
            deltaY = Math.min(-rectF.top, deltaY);
            canScrollVertically = true;
        } else if (deltaY < 0 && rectF.bottom > getHeight()) {
            deltaY = Math.max(-(rectF.bottom - getHeight()), deltaY);
            canScrollVertically = true;
        } else {
            canScrollVertically = false;
        }

        if (canScrollHorizontally) {
            mMatrix.postTranslate(deltaX, 0);
        }
        if (canScrollVertically) {
            mMatrix.postTranslate(0, deltaY);

        }
        if (!canScrollHorizontally && !canScrollVertically) {
            getParent().requestDisallowInterceptTouchEvent(false);  // 不能滑动，通知父容器拦截事件
        }
        setImageMatrix(mMatrix);
        return true;
    }

    /**
     * 获取变换后的图像位置信息
     *
     * @return
     */
    private RectF getMatrixRectF() {
        RectF rectF = new RectF();
        Drawable drawable = getDrawable();
        rectF.set(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());   // 注意这里需要提供原始图片的宽高
        mMatrix.mapRect(rectF);
        return rectF;
    }

    @Override
    public void onLongPress(MotionEvent e) {
        if (mOnLongPressListener != null) {
            mOnLongPressListener.onLongPress(this);
        }
    }

    @Override
    public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        // TODO: 实现快速滑动
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        boolean result;
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) {
            mIsScaling = true;
        }
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_UP) { // 双指缩放结束后的回弹动画
            final float start = getCurrentScale();
            final float end;
            final float pivotX;
            final float pivotY = getHeight() / 2.0f;
            boolean isOverScale = false;
            if (start < mInitScale) {
                end = mInitScale;
                pivotX = getWidth() / 2.0f;
                isOverScale = true;
            } else if (start > mMaxScale) {
                end = mMaxScale;
                pivotX = mPivotX;
                isOverScale = true;
            } else {
                end = 0f;
                pivotX = 0f;
            }
            if (isOverScale) {
                scaleAnimate(start, end, pivotX, pivotY);
            }
            mLastX = event.getX(0);
            mLastY = event.getY(0);
            mIsScaling = false;
            return true;    // 这里返回true是避免GestureDetector处理MotionEvent.ACTION_POINTER_UP事件，从而使双指缩放结束后可以跟滑动衔接起来
        }

        if (mIsScaling) {
            result = mScaleGestureDetector.onTouchEvent(event);
        } else {
            result = mGestureDetector.onTouchEvent(event);
        }
        return result;
    }

    /**
     * 实现自动缩放动画
     * @param start
     * @param end
     * @param pivotX
     * @param pivotY
     */
    private void scaleAnimate(float start, float end, final float pivotX, final float pivotY) {
        ValueAnimator animator = ValueAnimator.ofFloat(start, end).setDuration(300);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                final float currentScale = getCurrentScale();
                float targetScale = (float) animation.getAnimatedValue();
                targetScale = targetScale / currentScale;
                mMatrix.postScale(targetScale, targetScale, pivotX, pivotY);
                checkImageBounds();
                setImageMatrix(mMatrix);
            }
        });
        animator.start();
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
        Toast.makeText(getContext(), "这是单击事件", Toast.LENGTH_SHORT).show();
        if (mOnSingleTapListener != null) {
            mOnSingleTapListener.onSingleTapConfirm(this);
        }

        return true;
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
        final float currentScale = getCurrentScale();
        if (currentScale >= mDoubleTapScaleUp) {
            scaleAnimate(currentScale, mInitScale, e.getX(), e.getY());
        } else {
            scaleAnimate(currentScale, mDoubleTapScaleUp, e.getX(), getHeight() / 2.0f);
        }
        return true;
    }

    @Override
    public boolean onDoubleTapEvent(MotionEvent e) {
        return false;
    }

    public void setOnSingleTapListener(OnSingleTapListener l) {
        if (l != null) {
            mOnSingleTapListener = l;
        }
    }

    public void setOnLongPressListener(OnLongPressListener l) {
        if (l != null) {
            mOnLongPressListener = l;
        }
    }

    public interface OnSingleTapListener {
        void onSingleTapConfirm(View view);
    }

    public interface OnLongPressListener {
        void onLongPress(View view);
    }
}
