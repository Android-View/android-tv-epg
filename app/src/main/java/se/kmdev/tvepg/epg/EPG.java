package se.kmdev.tvepg.epg;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Scroller;
import android.widget.TextView;

import com.google.common.collect.Maps;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.joda.time.LocalDateTime;

import java.io.InterruptedIOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

import se.kmdev.tvepg.R;
import se.kmdev.tvepg.epg.domain.EPGChannel;
import se.kmdev.tvepg.epg.domain.EPGEvent;
import se.kmdev.tvepg.epg.domain.EPGState;
import se.kmdev.tvepg.epg.misc.EPGDataImpl;
import se.kmdev.tvepg.epg.misc.EPGUtil;

/**
 * Classic EPG, electronic program guide, that scrolls both horizontal, vertical and diagonal.
 * It utilize onDraw() to draw the graphic on screen. So there are some private helper methods calculating positions etc.
 * Listed on Y-axis are channels and X-axis are programs/events. Data is added to EPG by using setEPGData()
 * and pass in an EPGData implementation. A click listener can be added using setEPGClickListener().
 * Created by Kristoffer, http://kmdev.se
 */
public class EPG extends ViewGroup {

    public final String TAG = getClass().getSimpleName();
    public static final int DAYS_BACK_MILLIS = 3 * 24 * 60 * 60 * 1000;        // 3 days
    public static final int DAYS_FORWARD_MILLIS = 3 * 24 * 60 * 60 * 1000;     // 3 days
    public static final int HOURS_IN_VIEWPORT_MILLIS = 2 * 60 * 60 * 1000;     // 2 hours
    public static final int TIME_LABEL_SPACING_MILLIS = 30 * 60 * 1000;        // 30 minutes

    private SimpleDateFormat programTimeFormatLong = new SimpleDateFormat("dd MMM, EEEE  HH:mm");
    private SimpleDateFormat programTimeFormat = new SimpleDateFormat("HH:mm");

    private final Rect mClipRect;
    private final Rect mDrawingRect;
    private final Rect mMeasuringRect;
    private final Paint mPaint;
    private final Scroller mScroller;
    private final GestureDetector mGestureDetector;

    private final int mChannelLayoutMargin;
    private final int mChannelLayoutPadding;
    private final int mChannelLayoutHeight;
    private final int mChannelLayoutWidth;
    private final int mChannelLayoutBackground;
    private final int mEventLayoutBackground;
    private final int mEventLayoutBackgroundCurrent;
    private final int mEventLayoutBackgroundSelected;
    private final int mEventLayoutTextColor;
    private final int mEventLayoutTextSize;
    private final int mTimeBarLineWidth;
    private final int mTimeBarLineColor;
    private final int mTimeBarHeight;
    private final int mTimeBarTextSize;

    private final int mResetButtonSize;
    private final int mResetButtonMargin;
    private final Bitmap mResetButtonIcon;

    private final int mEPGBackground;
    private final Map<String, Bitmap> mChannelImageCache;
    private final Map<String, Target> mChannelImageTargetCache;

    private EPGClickListener mClickListener;
    private int mMaxHorizontalScroll;
    private int mMaxVerticalScroll;
    private long mMillisPerPixel;
    private long mTimeOffset;
    private long mTimeLowerBoundary;
    private long mTimeUpperBoundary;

    //TODO: find out why grid is shifted -> because of channels bar?
    private long mMargin = 200000;

    private EPGData epgData = null;
    private EPGEvent selectedEvent = null;

    private static ImageView programImage = null;
    private AsyncTask loadProgramImage = null;
    private TextView currentEventTextView;
    private TextView currentEventTimeTextView;
    private int orientation;

    public static int screenWidth;
    public static int screenHeight;

    public EPG(Context context) {
        this(context, null);
    }

    public EPG(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public EPG(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        setWillNotDraw(false);

        resetBoundaries();

        mDrawingRect = new Rect();
        mClipRect = new Rect();
        mMeasuringRect = new Rect();
        mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        mGestureDetector = new GestureDetector(context, new OnGestureListener());
        mChannelImageCache = Maps.newHashMap();
        mChannelImageTargetCache = Maps.newHashMap();

        // Adding some friction that makes the epg less flappy.
        mScroller = new Scroller(context);
        mScroller.setFriction(0.2f);

        mEPGBackground = getResources().getColor(R.color.epg_background);

        mChannelLayoutMargin = getResources().getDimensionPixelSize(R.dimen.epg_channel_layout_margin);
        mChannelLayoutPadding = getResources().getDimensionPixelSize(R.dimen.epg_channel_layout_padding);
        mChannelLayoutHeight = getResources().getDimensionPixelSize(R.dimen.epg_channel_layout_height);
        mChannelLayoutWidth = getResources().getDimensionPixelSize(R.dimen.epg_channel_layout_width);
        mChannelLayoutBackground = getResources().getColor(R.color.epg_channel_layout_background);

        mEventLayoutBackground = getResources().getColor(R.color.epg_event_layout_background);
        mEventLayoutBackgroundCurrent = getResources().getColor(R.color.epg_event_layout_background_current);
        mEventLayoutBackgroundSelected = getResources().getColor(R.color.epg_event_layout_background_selected);
        mEventLayoutTextColor = getResources().getColor(R.color.epg_event_layout_text);
        mEventLayoutTextSize = getResources().getDimensionPixelSize(R.dimen.epg_event_layout_text);

        mTimeBarHeight = getResources().getDimensionPixelSize(R.dimen.epg_time_bar_height);
        mTimeBarTextSize = getResources().getDimensionPixelSize(R.dimen.epg_time_bar_text);
        mTimeBarLineWidth = getResources().getDimensionPixelSize(R.dimen.epg_time_bar_line_width);
        mTimeBarLineColor = getResources().getColor(R.color.epg_time_bar);

        mResetButtonSize = getResources().getDimensionPixelSize(R.dimen.epg_reset_button_size);
        mResetButtonMargin = getResources().getDimensionPixelSize(R.dimen.epg_reset_button_margin);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.outWidth = mResetButtonSize;
        options.outHeight = mResetButtonSize;
        mResetButtonIcon = BitmapFactory.decodeResource(getResources(), R.drawable.reset, options);

    }


    @Override
    //save state to recover state after restart or screen rotation
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        EPGState epgState = new EPGState(superState);
        epgState.setCurrentEvent(this.selectedEvent);
        return epgState;
    }

    @Override
    //recover the state
    public void onRestoreInstanceState(Parcelable state) {
        if(!(state instanceof EPGState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        EPGState epgState = (EPGState)state;
        super.onRestoreInstanceState(epgState.getSuperState());
        this.selectedEvent = epgState.getCurrentEvent();
    }

    private int getChannelAreaWidth() {
        return mChannelLayoutWidth + mChannelLayoutPadding + mChannelLayoutMargin;
    }

    private int getProgramAreaWidth() {
        return getWidth() - getChannelAreaWidth();
    }

    @Override
    protected void onDraw(Canvas canvas) {

        if (epgData != null && epgData.hasData()) {
            mTimeLowerBoundary = getTimeFrom(getScrollX());
            mTimeUpperBoundary = getTimeFrom(getScrollX() + getWidth());

            Rect drawingRect = mDrawingRect;
            drawingRect.left = getScrollX();
            drawingRect.top = getScrollY();
            drawingRect.right = drawingRect.left + getWidth();
            drawingRect.bottom = drawingRect.top + getHeight();

            drawChannelListItems(canvas, drawingRect);
            drawEvents(canvas, drawingRect);
            drawTimebar(canvas, drawingRect);
            drawTimeLine(canvas, drawingRect);
            drawResetButton(canvas, drawingRect);

            // If scroller is scrolling/animating do scroll. This applies when doing a fling.
            if (mScroller.computeScrollOffset()) {
                scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        recalculateAndRedraw(this.selectedEvent, false);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }


    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        //return mGame.handleMotionEvent(event);
        return false;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        //return mGame.handleKeyEvent(event);
        return false;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        mTimeLowerBoundary = getTimeFrom(getScrollX());
        mTimeUpperBoundary = getTimeFrom(getScrollX() + getWidth());

        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            recalculateAndRedraw(null, true);
        } else if (this.selectedEvent != null) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (this.selectedEvent.getNextEvent() != null) {
                    this.selectedEvent.selected = false;
                    this.selectedEvent = this.selectedEvent.getNextEvent();
                    this.selectedEvent.selected = true;
                    optimizeVisibility(this.selectedEvent, true);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (this.selectedEvent.getPreviousEvent() != null) {
                    this.selectedEvent.selected = false;
                    this.selectedEvent = this.selectedEvent.getPreviousEvent();
                    this.selectedEvent.selected = true;
                    optimizeVisibility(this.selectedEvent, true);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                if (this.selectedEvent.getChannel().getPreviousChannel() != null) {
                    long lowerBoundary = Math.max(mTimeLowerBoundary, this.selectedEvent.getStart());
                    long upperBoundary = Math.min(mTimeUpperBoundary, this.selectedEvent.getEnd());
                    long eventMiddleTime = (lowerBoundary + upperBoundary) / 2;
                    EPGEvent previousChannelEvent = getProgramAtTime(this.selectedEvent.getChannel().getPreviousChannel().getChannelID(), eventMiddleTime);
                    if (previousChannelEvent != null) {
                        this.selectedEvent.selected = false;
                        this.selectedEvent = previousChannelEvent;
                        this.selectedEvent.selected = true;
                    }
                    optimizeVisibility(this.selectedEvent, true);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (this.selectedEvent.getChannel().getNextChannel() != null) {
                    long lowerBoundary = Math.max(mTimeLowerBoundary, this.selectedEvent.getStart());
                    long upperBoundary = Math.min(mTimeUpperBoundary, this.selectedEvent.getEnd());
                    long eventMiddleTime = (lowerBoundary + upperBoundary) / 2;
                    EPGEvent nextChannelEvent = getProgramAtTime(this.selectedEvent.getChannel().getNextChannel().getChannelID(), eventMiddleTime);
                    if (nextChannelEvent != null) {
                        this.selectedEvent.selected = false;
                        this.selectedEvent = nextChannelEvent;
                        this.selectedEvent.selected = true;
                    }
                    optimizeVisibility(this.selectedEvent, true);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_R1 || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                gotoNextDay(this.selectedEvent);
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_L1 || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND) {
                gotoPreviousDay(this.selectedEvent);
            }

            loadProgramDetails(this.selectedEvent);
            redraw();
        }
        return super.onKeyDown(keyCode, event);
    }


    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    private void drawResetButton(Canvas canvas, Rect drawingRect) {
        // Show button when scrolled 1/3 of screen width from current time
        final long threshold = getWidth() / 3;
        if (Math.abs(getXPositionStart() - getScrollX()) > threshold) {
            drawingRect = calculateResetButtonHitArea();
            mPaint.setColor(mTimeBarLineColor);
            canvas.drawCircle(drawingRect.right - (mResetButtonSize / 2),
                    drawingRect.bottom - (mResetButtonSize / 2),
                    Math.min(drawingRect.width(), drawingRect.height()) / 2,
                    mPaint);

            drawingRect.left += mResetButtonMargin;
            drawingRect.right -= mResetButtonMargin;
            drawingRect.top += mResetButtonMargin;
            drawingRect.bottom -= mResetButtonMargin;
            canvas.drawBitmap(mResetButtonIcon, null, drawingRect, mPaint);
        }
    }

    private void drawTimebarBottomStroke(Canvas canvas, Rect drawingRect) {
        drawingRect.left = getScrollX();
        drawingRect.top = getScrollY() + mTimeBarHeight;
        drawingRect.right = drawingRect.left + getWidth();
        drawingRect.bottom = drawingRect.top + mChannelLayoutMargin;

        // Bottom stroke
        mPaint.setColor(mEPGBackground);
        canvas.drawRect(drawingRect, mPaint);
    }

    private void drawTimebar(Canvas canvas, Rect drawingRect) {
        drawingRect.left = getScrollX() + mChannelLayoutWidth + mChannelLayoutMargin;
        drawingRect.top = getScrollY();
        drawingRect.right = drawingRect.left + getWidth();
        drawingRect.bottom = drawingRect.top + mTimeBarHeight;

        mClipRect.left = getScrollX() + mChannelLayoutWidth + mChannelLayoutMargin;
        mClipRect.top = getScrollY();
        mClipRect.right = getScrollX() + getWidth();
        mClipRect.bottom = mClipRect.top + mTimeBarHeight;

        canvas.save();
        canvas.clipRect(mClipRect);

        // Background
        mPaint.setColor(mChannelLayoutBackground);
        canvas.drawRect(drawingRect, mPaint);

        // Time stamps
        mPaint.setColor(mEventLayoutTextColor);
        mPaint.setTextSize(mTimeBarTextSize);

        for (int i = 0; i < HOURS_IN_VIEWPORT_MILLIS / TIME_LABEL_SPACING_MILLIS; i++) {
            // Get time and round to nearest half hour
            final long time = TIME_LABEL_SPACING_MILLIS *
                    (((mTimeLowerBoundary + (TIME_LABEL_SPACING_MILLIS * i)) +
                            (TIME_LABEL_SPACING_MILLIS / 2)) / TIME_LABEL_SPACING_MILLIS);

            canvas.drawText(EPGUtil.getShortTime(time),
                    getXFrom(time),
                    drawingRect.top + (((drawingRect.bottom - drawingRect.top) / 2) + (mTimeBarTextSize / 2)), mPaint);
        }

        canvas.restore();

        drawTimebarDayIndicator(canvas, drawingRect);
        drawTimebarBottomStroke(canvas, drawingRect);
    }

    private void drawTimebarDayIndicator(Canvas canvas, Rect drawingRect) {
        drawingRect.left = getScrollX();
        drawingRect.top = getScrollY();
        drawingRect.right = drawingRect.left + mChannelLayoutWidth;
        drawingRect.bottom = drawingRect.top + mTimeBarHeight;

        // Background
        mPaint.setColor(mChannelLayoutBackground);
        canvas.drawRect(drawingRect, mPaint);

        // Text
        mPaint.setColor(mEventLayoutTextColor);
        mPaint.setTextSize(mTimeBarTextSize);
        mPaint.setTextAlign(Paint.Align.CENTER);
        canvas.drawText(EPGUtil.getEPGdayName(mTimeLowerBoundary),
                drawingRect.left + ((drawingRect.right - drawingRect.left) / 2),
                drawingRect.top + (((drawingRect.bottom - drawingRect.top) / 2) + (mTimeBarTextSize / 2)), mPaint);

        mPaint.setTextAlign(Paint.Align.LEFT);
    }

    private void drawTimeLine(Canvas canvas, Rect drawingRect) {
        long now = System.currentTimeMillis();

        if (shouldDrawTimeLine(now)) {
            drawingRect.left = getXFrom(now);
            drawingRect.top = getScrollY();
            drawingRect.right = drawingRect.left + mTimeBarLineWidth;
            drawingRect.bottom = drawingRect.top + getHeight();

            mPaint.setColor(mTimeBarLineColor);
            canvas.drawRect(drawingRect, mPaint);
        }

    }

    private void selectEvent() {

    }

    private void drawEvents(Canvas canvas, Rect drawingRect) {
        final int firstPos = getFirstVisibleChannelPosition();
        final int lastPos = getLastVisibleChannelPosition();

        for (int channelPos = firstPos; channelPos <= lastPos; channelPos++) {

            // Set clip rectangle
            mClipRect.left = getScrollX() + mChannelLayoutWidth + mChannelLayoutMargin;
            mClipRect.top = getTopFrom(channelPos);
            mClipRect.right = getScrollX() + getWidth();
            mClipRect.bottom = mClipRect.top + mChannelLayoutHeight;

            canvas.save();
            canvas.clipRect(mClipRect);

            // Draw each event
            boolean foundFirst = false;

            List<EPGEvent> epgEvents = epgData.getEvents(channelPos);

            for (EPGEvent event : epgEvents) {
                if (isEventVisible(event.getStart(), event.getEnd())) {
                    drawEvent(canvas, channelPos, event, drawingRect);
                    foundFirst = true;
                } else if (foundFirst) {
                    break;
                }
            }

            canvas.restore();
        }

    }

    private void drawEvent(final Canvas canvas, final int channelPosition, final EPGEvent event, final Rect drawingRect) {

        setEventDrawingRectangle(channelPosition, event.getStart(), event.getEnd(), drawingRect);

        // Background
        if (event.isSelected()) {
            mPaint.setColor(mEventLayoutBackgroundSelected);
        } else if (event.isCurrent()) {
            mPaint.setColor(mEventLayoutBackgroundCurrent);
        } else {
            mPaint.setColor(mEventLayoutBackground);
        }
        canvas.drawRect(drawingRect, mPaint);

        // Add left and right inner padding
        drawingRect.left += mChannelLayoutPadding + 16;
        drawingRect.right -= mChannelLayoutPadding;

        // Text
        mPaint.setColor(mEventLayoutTextColor);
        mPaint.setTextSize(mEventLayoutTextSize);

        // Move drawing.top so text will be centered (text is drawn bottom>up)
        mPaint.getTextBounds(event.getTitle(), 0, event.getTitle().length(), mMeasuringRect);
        drawingRect.top += (((drawingRect.bottom - drawingRect.top) / 2) + (mMeasuringRect.height() / 2));

        String title = event.getTitle();
        title = title.substring(0,
                mPaint.breakText(title, true, drawingRect.right - drawingRect.left, null));
        canvas.drawText(title, drawingRect.left, drawingRect.top, mPaint);

    }

    private void setEventDrawingRectangle(final int channelPosition, final long start, final long end, final Rect drawingRect) {
        drawingRect.left = getXFrom(start);
        drawingRect.top = getTopFrom(channelPosition);
        drawingRect.right = getXFrom(end) - mChannelLayoutMargin;
        drawingRect.bottom = drawingRect.top + mChannelLayoutHeight;
    }

    private void drawChannelListItems(Canvas canvas, Rect drawingRect) {
        // Background
        mMeasuringRect.left = getScrollX();
        mMeasuringRect.top = getScrollY();
        mMeasuringRect.right = drawingRect.left + mChannelLayoutWidth;
        mMeasuringRect.bottom = mMeasuringRect.top + getHeight();

        mPaint.setColor(mChannelLayoutBackground);
        canvas.drawRect(mMeasuringRect, mPaint);

        final int firstPos = getFirstVisibleChannelPosition();
        final int lastPos = getLastVisibleChannelPosition();

        for (int pos = firstPos; pos <= lastPos; pos++) {
            drawChannelItem(canvas, pos, drawingRect);
        }
    }

    private void drawChannelItem(final Canvas canvas, int position, Rect drawingRect) {
        drawingRect.left = getScrollX();
        drawingRect.top = getTopFrom(position);
        drawingRect.right = drawingRect.left + mChannelLayoutWidth;
        drawingRect.bottom = drawingRect.top + mChannelLayoutHeight;

        // Loading channel image into target for
        final String imageURL = epgData.getChannel(position).getImageURL();

        if (mChannelImageCache.containsKey(imageURL)) {
            Bitmap image = mChannelImageCache.get(imageURL);
            drawingRect = getDrawingRectForChannelImage(drawingRect, image);
            canvas.drawBitmap(image, null, drawingRect, null);
        } else {
            final int smallestSide = Math.min(mChannelLayoutHeight, mChannelLayoutWidth);

            if (!mChannelImageTargetCache.containsKey(imageURL)) {
                mChannelImageTargetCache.put(imageURL, new Target() {
                    @Override
                    public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom from) {
                        mChannelImageCache.put(imageURL, bitmap);
                        redraw();
                        mChannelImageTargetCache.remove(imageURL);
                    }

                    @Override
                    public void onBitmapFailed(Drawable errorDrawable) {

                    }

                    @Override
                    public void onPrepareLoad(Drawable placeHolderDrawable) {

                    }
                });

                EPGUtil.loadImageInto(getContext(), imageURL, smallestSide, smallestSide, mChannelImageTargetCache.get(imageURL));
            }

        }
    }

    private Rect getDrawingRectForChannelImage(Rect drawingRect, Bitmap image) {
        drawingRect.left += mChannelLayoutPadding;
        drawingRect.top += mChannelLayoutPadding;
        drawingRect.right -= mChannelLayoutPadding;
        drawingRect.bottom -= mChannelLayoutPadding;

        final int imageWidth = image.getWidth();
        final int imageHeight = image.getHeight();
        final float imageRatio = imageHeight / (float) imageWidth;

        final int rectWidth = drawingRect.right - drawingRect.left;
        final int rectHeight = drawingRect.bottom - drawingRect.top;

        // Keep aspect ratio.
        if (imageWidth > imageHeight) {
            final int padding = (int) (rectHeight - (rectWidth * imageRatio)) / 2;
            drawingRect.top += padding;
            drawingRect.bottom -= padding;
        } else if (imageWidth <= imageHeight) {
            final int padding = (int) (rectWidth - (rectHeight / imageRatio)) / 2;
            drawingRect.left += padding;
            drawingRect.right -= padding;
        }

        return drawingRect;
    }

    private boolean shouldDrawTimeLine(long now) {
        return now >= mTimeLowerBoundary && now < mTimeUpperBoundary;
    }

    private boolean isEventVisible(final long start, final long end) {
        return (start >= mTimeLowerBoundary && start <= mTimeUpperBoundary)
                || (end >= mTimeLowerBoundary && end <= mTimeUpperBoundary)
                || (start <= mTimeLowerBoundary && end >= mTimeUpperBoundary);
    }

    private long calculatedBaseLine() {
        return LocalDateTime.now().toDateTime().minusMillis(DAYS_BACK_MILLIS).getMillis();
    }

    private int getFirstVisibleChannelPosition() {
        final int y = getScrollY();

        int position = (y - mChannelLayoutMargin - mTimeBarHeight)
                / (mChannelLayoutHeight + mChannelLayoutMargin);

        if (position < 0) {
            position = 0;
        }
        return position;
    }

    private int getLastVisibleChannelPosition() {
        final int y = getScrollY();
        final int totalChannelCount = epgData.getChannelCount();
        final int screenHeight = getHeight();
        int position = (y + screenHeight + mTimeBarHeight - mChannelLayoutMargin)
                / (mChannelLayoutHeight + mChannelLayoutMargin);

        if (position > totalChannelCount - 1) {
            position = totalChannelCount - 1;
        }

        // Add one extra row if we don't fill screen with current..
        return (y + screenHeight) > (position * mChannelLayoutHeight) && position < totalChannelCount - 1 ? position + 1 : position;
    }

    private void calculateMaxHorizontalScroll() {
        mMaxHorizontalScroll = (int) ((DAYS_BACK_MILLIS + DAYS_FORWARD_MILLIS - HOURS_IN_VIEWPORT_MILLIS) / mMillisPerPixel);
    }

    private void calculateMaxVerticalScroll() {
        final int maxVerticalScroll = getTopFrom(epgData.getChannelCount() - 1) + mChannelLayoutHeight;
        mMaxVerticalScroll = maxVerticalScroll < getHeight() ? 0 : maxVerticalScroll - getHeight();
    }

    private int getXFrom(long time) {
        return (int) ((time - mTimeOffset) / mMillisPerPixel) + mChannelLayoutMargin
                + mChannelLayoutWidth + mChannelLayoutMargin;
    }

    private int getTopFrom(int position) {
        int y = position * (mChannelLayoutHeight + mChannelLayoutMargin)
                + mChannelLayoutMargin + mTimeBarHeight;
        return y;
    }

    private long getTimeFrom(int x) {
        return (x * mMillisPerPixel) + mTimeOffset;
    }

    private long calculateMillisPerPixel() {
        return HOURS_IN_VIEWPORT_MILLIS / (getResources().getDisplayMetrics().widthPixels - mChannelLayoutWidth - mChannelLayoutMargin);
    }

    private int getXPositionStart() {
        return getXFrom(System.currentTimeMillis() - (HOURS_IN_VIEWPORT_MILLIS / 2));
    }

    private void resetBoundaries() {
        mMillisPerPixel = calculateMillisPerPixel();
        mTimeOffset = calculatedBaseLine();
        mTimeLowerBoundary = getTimeFrom(0);
        mTimeUpperBoundary = getTimeFrom(getWidth());
    }

    private Rect calculateChannelsHitArea() {
        mMeasuringRect.top = mTimeBarHeight;
        int visibleChannelsHeight = epgData.getChannelCount() * (mChannelLayoutHeight + mChannelLayoutMargin);
        mMeasuringRect.bottom = visibleChannelsHeight < getHeight() ? visibleChannelsHeight : getHeight();
        mMeasuringRect.left = 0;
        mMeasuringRect.right = mChannelLayoutWidth;
        return mMeasuringRect;
    }

    private Rect calculateProgramsHitArea() {
        mMeasuringRect.top = mTimeBarHeight;
        int visibleChannelsHeight = epgData.getChannelCount() * (mChannelLayoutHeight + mChannelLayoutMargin);
        mMeasuringRect.bottom = visibleChannelsHeight < getHeight() ? visibleChannelsHeight : getHeight();
        mMeasuringRect.left = mChannelLayoutWidth;
        mMeasuringRect.right = getWidth();
        return mMeasuringRect;
    }

    private Rect calculateResetButtonHitArea() {
        mMeasuringRect.left = getScrollX() + getWidth() - mResetButtonSize - mResetButtonMargin;
        mMeasuringRect.top = getScrollY() + getHeight() - mResetButtonSize - mResetButtonMargin;
        mMeasuringRect.right = mMeasuringRect.left + mResetButtonSize;
        mMeasuringRect.bottom = mMeasuringRect.top + mResetButtonSize;
        return mMeasuringRect;
    }

    private int getChannelPosition(int y) {
        y -= mTimeBarHeight;
        int channelPosition = (y + mChannelLayoutMargin)
                / (mChannelLayoutHeight + mChannelLayoutMargin);

        return epgData.getChannelCount() == 0 ? -1 : channelPosition;
    }

    private int getProgramPosition(int channelPosition, long time) {
        List<EPGEvent> events = epgData.getEvents(channelPosition);

        if (events != null) {

            for (int eventPos = 0; eventPos < events.size(); eventPos++) {
                EPGEvent event = events.get(eventPos);

                if (event.getStart() <= time && event.getEnd() >= time) {
                    return eventPos;
                }
            }
        }
        return -1;
    }

    private EPGEvent getProgramAtTime(int channelPosition, long time) {
        List<EPGEvent> events = epgData.getEvents(channelPosition);

        if (events != null) {

            for (int eventPos = 0; eventPos < events.size(); eventPos++) {
                EPGEvent event = events.get(eventPos);

                if (event.getStart() <= time && event.getEnd() >= time) {
                    return event;
                }
            }
        }
        return null;
    }

    /**
     * Add click listener to the EPG.
     *
     * @param epgClickListener to add.
     */
    public void setEPGClickListener(EPGClickListener epgClickListener) {
        mClickListener = epgClickListener;
    }

    /**
     * Add data to EPG. This must be set for EPG to able to draw something.
     *
     * @param epgData pass in any implementation of EPGData.
     */
    public void setEPGData(EPGData epgData) {
        this.epgData = mergeEPGData(this.epgData, epgData);
        //this.epgData = epgData;
    }

    private EPGData mergeEPGData(EPGData oldData, EPGData newData) {
        try {
            if (oldData == null) {
                Map<EPGChannel, List<EPGEvent>> map = Maps.newLinkedHashMap();
                oldData = new EPGDataImpl(map);
            }
            if (newData != null) {
                for (int i = 0; i < newData.getChannelCount(); i++) {
                    EPGChannel newChannel = newData.getChannel(i);
                    EPGChannel oldChannel = oldData.getOrCreateChannel(newChannel.getName());
                    for (int j = 0; j < newChannel.getEvents().size(); j++) {
                        EPGEvent newEvent = newChannel.getEvents().get(j);
                        oldChannel.addEvent(newEvent);
                    }
                }
            }
            return oldData;
        } catch (Throwable e) {
            throw new RuntimeException("Could not merge EPG data: " + e.getClass().getSimpleName() + " " + e.getMessage(), e);
        }
    }

    /**
     * This will recalculate boundaries, maximal scroll and scroll to start position which is current time.
     * To be used on device rotation etc since the device height and width will change.
     *
     * @param withAnimation true if scroll to current position should be animated.
     */
    public void recalculateAndRedraw(EPGEvent selectedEvent, boolean withAnimation) {
        if (epgData != null && epgData.hasData()) {
            resetBoundaries();

            calculateMaxVerticalScroll();
            calculateMaxHorizontalScroll();

            //Select initial event
            if(selectedEvent!=null){
                selectEvent(selectedEvent, withAnimation);
            }else {
                selectEvent(epgData.getEvent(0, getProgramPosition(0, getTimeFrom(getXPositionStart() + (getWidth() / 2)))), withAnimation);
            }

            //re-center
            /*
            mScroller.startScroll(getScrollX(), getScrollY(),
                    getXPositionStart() - getScrollX(),
                    0, withAnimation ? 600 : 0);
*/
            redraw();

        }
    }

    /**
     * Does a invalidate() and requestLayout() which causes a redraw of screen.
     */
    public void redraw() {
        invalidate();
        requestLayout();
    }

    /**
     * Clears the local image cache for channel images. Can be used when leaving epg and you want to
     * free some memory. Images will be fetched again when loading EPG next time.
     */
    public void clearEPGImageCache() {
        mChannelImageCache.clear();
    }

    private void loadProgramDetails(EPGEvent epgEvent) {
        // load program details
        if (loadProgramImage != null && loadProgramImage.getStatus() != AsyncTask.Status.FINISHED) {
            loadProgramImage.cancel(true);
        }
        loadProgramImage = new AsyncLoadProgramImage(this, epgEvent).execute();
        //set program title
        currentEventTextView.setText(epgEvent.getTitle());
        //set program times
        currentEventTimeTextView.setText(programTimeFormatLong.format(epgEvent.getStart()) + " - " + programTimeFormat.format(epgEvent.getEnd()));
    }

    public void selectEvent(EPGEvent epgEvent, boolean withAnimation) {
        if (this.selectedEvent != null) {
            this.selectedEvent.selected = false;
        }
        epgEvent.selected = true;
        this.selectedEvent = epgEvent;
        optimizeVisibility(epgEvent, withAnimation);
        loadProgramDetails(epgEvent);


        //redraw to get the coloring of the selected event
        redraw();
    }

    public void setProgramImageView(ImageView aProgramImage) {
        programImage = aProgramImage;
        updateImageCropping(programImage);
    }

    public void setCurrentEventTextView(TextView currentEventTextView) {
        this.currentEventTextView = currentEventTextView;
    }

    public void setCurrentEventTimeTextView(TextView currentEventTimeTextView) {
        this.currentEventTimeTextView = currentEventTimeTextView;
    }

    // Configuration.ORIENTATION_PORTRAIT or Configuration.ORIENTATION_LANDSCAPE
    public void setOrientation(int orientation) {
        this.orientation = orientation;
        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        screenWidth = dm.widthPixels;
        screenHeight = dm.heightPixels;
    }

    private static class AsyncLoadProgramImage extends AsyncTask<EPGEvent, Void, Bitmap> {

        private final EPG epg;
        EPGEvent epgEvent;

        public AsyncLoadProgramImage(EPG epg, EPGEvent epgEvent) {
            this.epg = epg;
            this.epgEvent = epgEvent;
        }

        @Override
        protected Bitmap doInBackground(EPGEvent... voids) {
            URL url;
            try {
                String uri = this.epgEvent.getProgramUrl();
                if (uri != null) {
                    url = new URL(uri);
                } else {
                    url = new URL("https://www.sound-star.nl/wp-content/uploads/2016/05/Dj-in-the-mix_Ultra-HD.jpg");
                }
                return BitmapFactory.decodeStream(url.openConnection().getInputStream());
            } catch (InterruptedIOException e) {
                //normal
                return null;
            } catch (Throwable e) {
                e.printStackTrace();
                //todo: return the default image
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bitmap bmp) {
            try {
                if (bmp != null) {
                    programImage.setImageBitmap(bmp);
                    updateImageCropping(programImage);
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private static void updateImageCropping(ImageView imageView) {
        final Matrix matrix = new Matrix();
        final float imageWidth = imageView.getDrawable().getIntrinsicWidth();
        final float imageHeight = imageView.getDrawable().getIntrinsicHeight();
        final float scaleRatio = screenWidth / imageWidth;
        matrix.postScale(scaleRatio, scaleRatio);
        matrix.postTranslate(0, -1 * imageHeight * 0.30f);
        imageView.setImageMatrix(matrix);
    }

    /**
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        //TODO: select a default eventItem when none is selected.

        mTimeLowerBoundary = getTimeFrom(getScrollX());
        mTimeUpperBoundary = getTimeFrom(getScrollX() + getWidth());

        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            recalculateAndRedraw(null, true);
        } else if (this.selectedEvent != null) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (this.selectedEvent.getNextEvent() != null) {
                    this.selectedEvent.selected = false;
                    this.selectedEvent = this.selectedEvent.getNextEvent();
                    this.selectedEvent.selected = true;
                    optimizeVisibility(this.selectedEvent, true);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (this.selectedEvent.getPreviousEvent() != null) {
                    this.selectedEvent.selected = false;
                    this.selectedEvent = this.selectedEvent.getPreviousEvent();
                    this.selectedEvent.selected = true;
                    optimizeVisibility(this.selectedEvent, true);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                if (this.selectedEvent.getChannel().getPreviousChannel() != null) {
                    long lowerBoundary = Math.max(mTimeLowerBoundary, this.selectedEvent.getStart());
                    long upperBoundary = Math.min(mTimeUpperBoundary, this.selectedEvent.getEnd());
                    long eventMiddleTime = (lowerBoundary + upperBoundary) / 2;
                    EPGEvent previousChannelEvent = getProgramAtTime(this.selectedEvent.getChannel().getPreviousChannel().getChannelID(), eventMiddleTime);
                    if (previousChannelEvent != null) {
                        this.selectedEvent.selected = false;
                        this.selectedEvent = previousChannelEvent;
                        this.selectedEvent.selected = true;
                    }
                    optimizeVisibility(this.selectedEvent, true);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (this.selectedEvent.getChannel().getNextChannel() != null) {
                    long lowerBoundary = Math.max(mTimeLowerBoundary, this.selectedEvent.getStart());
                    long upperBoundary = Math.min(mTimeUpperBoundary, this.selectedEvent.getEnd());
                    long eventMiddleTime = (lowerBoundary + upperBoundary) / 2;
                    EPGEvent nextChannelEvent = getProgramAtTime(this.selectedEvent.getChannel().getNextChannel().getChannelID(), eventMiddleTime);
                    if (nextChannelEvent != null) {
                        this.selectedEvent.selected = false;
                        this.selectedEvent = nextChannelEvent;
                        this.selectedEvent.selected = true;
                    }
                    optimizeVisibility(this.selectedEvent, true);
                }
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_R1 || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
                gotoNextDay(this.selectedEvent);
            } else if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_L1 || event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_REWIND) {
                gotoPreviousDay(this.selectedEvent);
            }

            loadProgramDetails(this.selectedEvent);
            redraw();
        }
        return super.onKeyUp(keyCode, event);
    }
    */

    private void gotoPreviousDay(EPGEvent currentEvent) {
        //TODO
    }

    private void gotoNextDay(EPGEvent currentEvent) {
        //TODO
    }

    public void optimizeVisibility(EPGEvent epgEvent, boolean withAnimation) {

        long dT = 0;
        int dX = 0;
        int dY = 0;

        // calculate optimal Y position

        int minYVisible = getScrollY(); // is 0 when scrolled completely to top (first channel fully visible)
        int maxYVisible = minYVisible + getHeight();

        int currentChannelPosition = epgEvent.getChannel().getChannelID();
        int currentChannelTop = mTimeBarHeight + (currentChannelPosition * (mChannelLayoutHeight + mChannelLayoutMargin));
        int currentChannelBottom = currentChannelTop + mChannelLayoutHeight;

        if (currentChannelTop < minYVisible) {
            dY = currentChannelTop - minYVisible - mTimeBarHeight;
        } else if (currentChannelBottom > maxYVisible) {
            dY = currentChannelBottom - maxYVisible;
        }

        // calculate optimal X position

        mTimeLowerBoundary = getTimeFrom(getScrollX());
        mTimeUpperBoundary = getTimeFrom(getScrollX() + getProgramAreaWidth());
        if (epgEvent.getEnd() > mTimeUpperBoundary) {
            //we need to scroll the grid to the left
            dT = (mTimeUpperBoundary - epgEvent.getEnd() - mMargin) * -1;
            dX = Math.round(dT / mMillisPerPixel);
        }
        mTimeLowerBoundary = getTimeFrom(getScrollX());
        mTimeUpperBoundary = getTimeFrom(getScrollX() + getWidth());
        if (epgEvent.getStart() < mTimeLowerBoundary) {
            //we need to scroll the grid to the right
            dT = (this.selectedEvent.getStart() - mTimeLowerBoundary - mMargin);
            dX = Math.round(dT / mMillisPerPixel);
        }

        if (dX != 0 || dY != 0) {
            mScroller.startScroll(getScrollX(), getScrollY(), dX, dY, withAnimation ? 600 : 0);
        }

    }

    private class OnGestureListener extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onSingleTapUp(MotionEvent e) {

            // This is absolute coordinate on screen not taking scroll into account.
            int x = (int) e.getX();
            int y = (int) e.getY();

            // Adding scroll to clicked coordinate
            int scrollX = getScrollX() + x;
            int scrollY = getScrollY() + y;

            int channelPosition = getChannelPosition(scrollY);
            if (channelPosition != -1 && mClickListener != null) {
                if (calculateResetButtonHitArea().contains(scrollX, scrollY)) {
                    // Reset button clicked
                    mClickListener.onResetButtonClicked();
                } else if (calculateChannelsHitArea().contains(x, y)) {
                    // Channel area is clicked
                    mClickListener.onChannelClicked(channelPosition, epgData.getChannel(channelPosition));
                } else if (calculateProgramsHitArea().contains(x, y)) {
                    // Event area is clicked
                    int programPosition = getProgramPosition(channelPosition, getTimeFrom(getScrollX() + x - calculateProgramsHitArea().left));
                    if (programPosition != -1) {
                        mClickListener.onEventClicked(channelPosition, programPosition, epgData.getEvent(channelPosition, programPosition));
                    }
                }
            }

            return true;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2,
                                float distanceX, float distanceY) {
            int dx = (int) distanceX;
            int dy = (int) distanceY;
            int x = getScrollX();
            int y = getScrollY();


            // Avoid over scrolling
            if (x + dx < 0) {
                dx = 0 - x;
            }
            if (y + dy < 0) {
                dy = 0 - y;
            }
            if (x + dx > mMaxHorizontalScroll) {
                dx = mMaxHorizontalScroll - x;
            }
            if (y + dy > mMaxVerticalScroll) {
                dy = mMaxVerticalScroll - y;
            }

            scrollBy(dx, dy);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2,
                               float vX, float vY) {

            mScroller.fling(getScrollX(), getScrollY(), -(int) vX,
                    -(int) vY, 0, mMaxHorizontalScroll, 0, mMaxVerticalScroll);

            redraw();
            return true;
        }

        @Override
        public boolean onDown(MotionEvent e) {
            if (!mScroller.isFinished()) {
                mScroller.forceFinished(true);
                return true;
            }
            return true;
        }
    }
}
