/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.browser;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.os.Message;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.DecelerateInterpolator;
import android.webkit.ValueCallback;
import org.codeaurora.swe.WebView;
import android.widget.ImageView;

import com.android.browser.UrlInputView.StateListener;

/**
 * Ui for regular phone screen sizes
 */
public class PhoneUi extends BaseUi {

    private static final String LOGTAG = "PhoneUi";
    private static final int MSG_INIT_NAVSCREEN = 100;

    private NavScreen mNavScreen;
    private AnimScreen mAnimScreen;
    private NavigationBarPhone mNavigationBar;
    private Activity mBrowser;

    boolean mAnimating;
    boolean mShowNav = false;


    /**
     * @param browser
     * @param controller
     */
    public PhoneUi(Activity browser, UiController controller) {
        super(browser, controller);
        mNavigationBar = (NavigationBarPhone) mTitleBar.getNavigationBar();
        mBrowser = browser;
    }

    @Override
    public void onDestroy() {
        hideTitleBar();
    }

    @Override
    public void editUrl(boolean clearInput, boolean forceIME) {
        //Do nothing while at Nav show screen.
        if (mShowNav) return;
        super.editUrl(clearInput, forceIME);
    }

    @Override
    public boolean onBackKey() {
        if (showingNavScreen()) {
            mNavScreen.close(mUiController.getTabControl().getCurrentPosition());
            return true;
        }
        return super.onBackKey();
    }

    private boolean showingNavScreen() {
        return mNavScreen != null && mNavScreen.getVisibility() == View.VISIBLE;
    }

    @Override
    public boolean dispatchKey(int code, KeyEvent event) {
        return false;
    }

    @Override
    public void onProgressChanged(Tab tab) {
        super.onProgressChanged(tab);
        if (mNavScreen == null && getTitleBar().getHeight() > 0) {
            mHandler.sendEmptyMessage(MSG_INIT_NAVSCREEN);
        }
    }

    @Override
    protected void handleMessage(Message msg) {
        super.handleMessage(msg);
        if (msg.what == MSG_INIT_NAVSCREEN) {
            if (mNavScreen == null) {
                mNavScreen = new NavScreen(mActivity, mUiController, this);
                mCustomViewContainer.addView(mNavScreen, COVER_SCREEN_PARAMS);
                mNavScreen.setVisibility(View.GONE);
            }
            if (mAnimScreen == null) {
                mAnimScreen = new AnimScreen(mActivity);
                // initialize bitmaps
                //mAnimScreen.set(getTitleBar(), getWebView());
            }
        }
    }

    @Override
    public void setActiveTab(final Tab tab) {
        mTitleBar.cancelTitleBarAnimation(true);
        mTitleBar.setSkipTitleBarAnimations(true);
        super.setActiveTab(tab);

        //if at Nav screen show, detach tab like what showNavScreen() do.
        if (mShowNav) {
            detachTab(mActiveTab);
        }

        BrowserWebView view = (BrowserWebView) tab.getWebView();
        // TabControl.setCurrentTab has been called before this,
        // so the tab is guaranteed to have a webview
        if (view == null) {
            Log.e(LOGTAG, "active tab with no webview detected");
            return;
        }
        // Request focus on the top window.
        view.setTitleBar(mTitleBar);

        // update nav bar state
        mNavigationBar.onStateChanged(StateListener.STATE_NORMAL);
        updateLockIconToLatest(tab);
        mTitleBar.setSkipTitleBarAnimations(false);
    }

    // menu handling callbacks

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        updateMenuState(mActiveTab, menu);
        return true;
    }

    @Override
    public void updateMenuState(Tab tab, Menu menu) {
        MenuItem bm = menu.findItem(R.id.bookmarks_menu_id);
        if (bm != null) {
            bm.setVisible(!showingNavScreen());
        }
        MenuItem info = menu.findItem(R.id.page_info_menu_id);
        if (info != null) {
            info.setVisible(false);
        }

        if (showingNavScreen()) {
            setMenuItemVisibility(menu, R.id.history_menu_id, false);
            setMenuItemVisibility(menu, R.id.find_menu_id, false);
            menu.setGroupVisible(R.id.LIVE_MENU, false);
            setMenuItemVisibility(menu, R.id.save_snapshot_menu_id, false);
            menu.setGroupVisible(R.id.SNAPSHOT_MENU, false);
            menu.setGroupVisible(R.id.NAV_MENU, false);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (showingNavScreen()
                && (item.getItemId() != R.id.history_menu_id)
                && (item.getItemId() != R.id.snapshots_menu_id)) {
            hideNavScreen(mUiController.getTabControl().getCurrentPosition(), false);
        }
        return false;
    }

    @Override
    public void onContextMenuCreated(Menu menu) {
        hideTitleBar();
    }

    @Override
    public void onContextMenuClosed(Menu menu, boolean inLoad) {
        if (inLoad) {
            showTitleBar();
        }
    }

    // action mode callbacks

    @Override
    public void onActionModeStarted(ActionMode mode) {
        super.onActionModeStarted(mode);
        if (!isEditingUrl()) {
            hideTitleBar();
        }
    }

    @Override
    public void onActionModeFinished(boolean inLoad) {
        super.onActionModeFinished(inLoad);
        if (inLoad) {
            showTitleBar();
        }
    }

    @Override
    public boolean isWebShowing() {
        return super.isWebShowing() && !showingNavScreen();
    }

    @Override
    public void showWeb(boolean animate) {
        super.showWeb(animate);
        hideNavScreen(mUiController.getTabControl().getCurrentPosition(), animate);
    }

    void showNavScreen() {
        WebView webView = getWebView();
        if (webView != null) {
            mShowNav = true;
            dismissIME();
            mUiController.setBlockEvents(true);

            webView.getContentBitmapAsync(1.0f,
                            new Rect(),
                            new ValueCallback<Bitmap>() {
                                @Override
                                public void onReceiveValue(Bitmap bitmap) {
                                    onShowNavScreenContinue(bitmap);
                                }
                            });
        }
    }

    void onShowNavScreenContinue(Bitmap viewportBitmap) {

        if (mNavScreen == null) {
            mNavScreen = new NavScreen(mActivity, mUiController, this);
            mCustomViewContainer.addView(mNavScreen, COVER_SCREEN_PARAMS);
        } else {
            mNavScreen.setVisibility(View.VISIBLE);
            mNavScreen.setAlpha(1f);
            mNavScreen.refreshAdapter();
        }
        mActiveTab.capture();
        if (mAnimScreen == null) {
            mAnimScreen = new AnimScreen(mActivity);
        } else {
            mAnimScreen.mMain.setAlpha(1f);
            mAnimScreen.setScaleFactor(1f);
        }
        mAnimScreen.set(getTitleBar(), viewportBitmap);
        if (mAnimScreen.mMain.getParent() == null) {
            mCustomViewContainer.addView(mAnimScreen.mMain, COVER_SCREEN_PARAMS);
        }
        mCustomViewContainer.setVisibility(View.VISIBLE);
        mCustomViewContainer.bringToFront();
        mAnimScreen.mMain.layout(0, 0, mContentView.getWidth(),
                mContentView.getHeight());
        int fromLeft = 0;
        int fromTop = getTitleBar().getHeight();
        int fromRight = mContentView.getWidth();
        int fromBottom = mContentView.getHeight();
        int width = mActivity.getResources().getDimensionPixelSize(R.dimen.nav_tab_width);
        int height = mActivity.getResources().getDimensionPixelSize(R.dimen.nav_tab_height);
        int ntth = mActivity.getResources().getDimensionPixelSize(R.dimen.nav_tab_titleheight);
        int toLeft = (mContentView.getWidth() - width) / 2;
        int toTop = ((fromBottom - (ntth + height)) / 2 + ntth);
        int toRight = toLeft + width;
        int toBottom = toTop + height;
        float toScaleFactor = width / (float) mContentView.getWidth();
        ObjectAnimator tx = ObjectAnimator.ofInt(mAnimScreen.mContent, "left", fromLeft, toLeft);
        ObjectAnimator ty = ObjectAnimator.ofInt(mAnimScreen.mContent, "top", fromTop, toTop);
        ObjectAnimator tr = ObjectAnimator.ofInt(mAnimScreen.mContent, "right", fromRight, toRight);
        ObjectAnimator tb = ObjectAnimator.ofInt(mAnimScreen.mContent, "bottom", fromBottom, toBottom);
        ObjectAnimator sx = ObjectAnimator.ofFloat(mAnimScreen, "scaleFactor", 1f, toScaleFactor);
        ObjectAnimator navTabsIn = ObjectAnimator.ofFloat(mNavScreen.mToolbarLayout, "translationY",
                -mNavScreen.getResources().getDimensionPixelSize(R.dimen.toolbar_height), 0f);
        mAnimScreen.mContent.layout(fromLeft, fromTop, fromRight, fromBottom);
        mAnimScreen.setScaleFactor(1f);

        AnimatorSet inanim = new AnimatorSet();
        inanim.playTogether(tx, ty, tr, tb, sx, navTabsIn);
        inanim.setInterpolator(new DecelerateInterpolator());
        inanim.setDuration(200);

        ObjectAnimator disappear = ObjectAnimator.ofFloat(mAnimScreen.mMain, "alpha", 1f, 0f);
        disappear.setInterpolator(new DecelerateInterpolator());
        disappear.setDuration(100);

        AnimatorSet set1 = new AnimatorSet();
        set1.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                mContentView.setVisibility(View.GONE);
            }
            @Override
            public void onAnimationEnd(Animator anim) {
                mCustomViewContainer.removeView(mAnimScreen.mMain);
                finishAnimationIn();
                mUiController.setBlockEvents(false);
            }
        });
        set1.playSequentially(inanim, disappear);
        set1.start();
        mUiController.setBlockEvents(false);
    }

    private void finishAnimationIn() {
        if (showingNavScreen()) {
            // notify accessibility manager about the screen change
            mNavScreen.sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
            mTabControl.setOnThumbnailUpdatedListener(mNavScreen);
        }
    }

    void hideNavScreen(int position, boolean animate) {
        mShowNav = false;
        if (!showingNavScreen()) return;
        final Tab tab = mUiController.getTabControl().getTab(position);
        if ((tab == null) || !animate) {
            if (tab != null) {
                setActiveTab(tab);
            } else if (mTabControl.getTabCount() > 0) {
                // use a fallback tab
                setActiveTab(mTabControl.getCurrentTab());
            }
            mContentView.setVisibility(View.VISIBLE);
            finishAnimateOut();
            return;
        }
        NavTabView tabview = (NavTabView) mNavScreen.getTabView(position);
        if (tabview == null) {
            if (mTabControl.getTabCount() > 0) {
                // use a fallback tab
                setActiveTab(mTabControl.getCurrentTab());
            }
            mContentView.setVisibility(View.VISIBLE);
            finishAnimateOut();
            return;
        }
        mUiController.setBlockEvents(true);
        mUiController.setActiveTab(tab);
        mContentView.setVisibility(View.VISIBLE);
        if (mAnimScreen == null) {
            mAnimScreen = new AnimScreen(mActivity);
        }
        ImageView target = tabview.mImage;
        int width = target.getDrawable().getIntrinsicWidth();
        int height = target.getDrawable().getIntrinsicHeight();
        Bitmap bm = tab.getScreenshot();
        if (bm == null)
            bm = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        mAnimScreen.set(bm);
        if (mAnimScreen.mMain.getParent() == null) {
            mCustomViewContainer.addView(mAnimScreen.mMain, COVER_SCREEN_PARAMS);
        }
        mAnimScreen.mMain.layout(0, 0, mContentView.getWidth(),
                mContentView.getHeight());
        mNavScreen.mScroller.finishScroller();
        int toLeft = 0;
        int toTop = mTitleBar.calculateEmbeddedHeight();
        int toRight = mContentView.getWidth();
        int fromLeft = tabview.getLeft() + target.getLeft() - mNavScreen.mScroller.getScrollX();
        int fromTop = tabview.getTop() + target.getTop() - mNavScreen.mScroller.getScrollY();
        int fromRight = fromLeft + width;
        int fromBottom = fromTop + height;
        float scaleFactor = mContentView.getWidth() / (float) width;
        int toBottom = toTop + (int) (height * scaleFactor);
        mAnimScreen.mContent.setLeft(fromLeft);
        mAnimScreen.mContent.setTop(fromTop);
        mAnimScreen.mContent.setRight(fromRight);
        mAnimScreen.mContent.setBottom(fromBottom);
        mAnimScreen.setScaleFactor(1f);
        //ObjectAnimator fade2 = ObjectAnimator.ofFloat(mNavScreen, "alpha", 1f, 0f);
        //fade2.setDuration(100);
        AnimatorSet set = new AnimatorSet();
        ObjectAnimator animAppear = ObjectAnimator.ofFloat(mAnimScreen.mMain, "alpha", 0f, 1f);
        animAppear.setDuration(100);
        ObjectAnimator l = ObjectAnimator.ofInt(mAnimScreen.mContent, "left", fromLeft, toLeft);
        ObjectAnimator t = ObjectAnimator.ofInt(mAnimScreen.mContent, "top", fromTop, toTop);
        ObjectAnimator r = ObjectAnimator.ofInt(mAnimScreen.mContent, "right", fromRight, toRight);
        ObjectAnimator b = ObjectAnimator.ofInt(mAnimScreen.mContent, "bottom", fromBottom, toBottom);
        ObjectAnimator scale = ObjectAnimator.ofFloat(mAnimScreen, "scaleFactor", 1f, scaleFactor);
        set.playTogether(animAppear, l, t, r, b, scale);
        set.setInterpolator(new DecelerateInterpolator());
        set.setDuration(200);
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator anim) {
                checkTabReady();
            }
        });
        set.start();
    }


    private int mNumTries = 0;
    private void checkTabReady() {
        boolean isready = true;
        boolean zeroTries = mNumTries == 0;
        Tab tab = mUiController.getTabControl().getCurrentTab();
        BrowserWebView webview = null;
        if (tab == null)
            isready = false;
        else {
            webview = (BrowserWebView)tab.getWebView();
            if (webview == null) {
                isready = false;
            }
            else if (webview.hasCrashed()) {
                webview.reload();
                isready = true;
            } else {
                isready = webview.isReady();
            }
        }
        // Post only when not ready and not crashed
        if (!isready && mNumTries++ < 150) {
            mCustomViewContainer.postDelayed(new Runnable() {
                public void run() {
                    checkTabReady();
                }
            }, 17); //WebView is not ready.  check again in for next frame.
            return;
        }
        mNumTries = 0;
        final boolean hasCrashed = (webview == null) ? false : webview.hasCrashed();
        // fast path: don't wait if we've been ready for a while
        if (zeroTries) {
            fadeOutCustomViewContainer(hasCrashed);
            return;
        }
        mCustomViewContainer.postDelayed(new Runnable() {
            public void run() {
                fadeOutCustomViewContainer(hasCrashed);
            }
        }, 32); //WebView is ready, but give it extra 2 frame's time to display and finish the swaps
    }

    private void fadeOutCustomViewContainer(boolean hasCrashed) {
        ObjectAnimator otheralpha = ObjectAnimator.ofFloat(mCustomViewContainer, "alpha", 1f, 0f);
        if (hasCrashed)
            otheralpha.setDuration(300);
        else
            otheralpha.setDuration(100);
        otheralpha.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator anim) {
                mCustomViewContainer.removeView(mAnimScreen.mMain);
                finishAnimateOut();
                mUiController.setBlockEvents(false);
            }
        });
        otheralpha.setInterpolator(new DecelerateInterpolator());
        otheralpha.start();
    }

    private void finishAnimateOut() {
        mTabControl.setOnThumbnailUpdatedListener(null);
        mNavScreen.setVisibility(View.GONE);
        mCustomViewContainer.setAlpha(1f);
        mCustomViewContainer.setVisibility(View.GONE);
        mAnimScreen.set(null);
    }

    @Override
    public boolean needsRestoreAllTabs() {
        return false;
    }

    public void toggleNavScreen() {
        if (!showingNavScreen()) {
            showNavScreen();
        } else {
            hideNavScreen(mUiController.getTabControl().getCurrentPosition(), false);
        }
    }

    @Override
    public boolean shouldCaptureThumbnails() {
        return true;
    }

    static class AnimScreen {

        private View mMain;
        private ImageView mContent;
        private float mScale;

        public AnimScreen(Context ctx) {
            mMain = LayoutInflater.from(ctx).inflate(R.layout.anim_screen, null);
            mMain.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // just eat clicks when this view is visible
                }
            });
            mContent = (ImageView) mMain.findViewById(R.id.anim_screen_content);
            mContent.setScaleType(ImageView.ScaleType.MATRIX);
            mContent.setImageMatrix(new Matrix());
            mScale = 1.0f;
            setScaleFactor(getScaleFactor());
        }

        public void set(TitleBar tbar, Bitmap viewportBitmap) {
            if (tbar == null) {
                return;
            }
            mContent.setImageBitmap(viewportBitmap);
        }

        /*private Bitmap safeCreateBitmap(int width, int height) {
            if (width <= 0 || height <= 0) {
                Log.w(LOGTAG, "safeCreateBitmap failed! width: " + width
                        + ", height: " + height);
                return null;
            }
            return Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
        }*/

        public void set(Bitmap image) {
            mContent.setImageBitmap(image);
        }

        private void setScaleFactor(float sf) {
            mScale = sf;
            Matrix m = new Matrix();
            m.postScale(sf, sf);
            mContent.setImageMatrix(m);
        }

        private float getScaleFactor() {
            return mScale;
        }

    }

}
