package com.webhtml.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class PullRefresh {

    private final Context context;
    private final WebView webView;
    private CustomSwipeRefreshLayout swipeRefreshLayout;

    public PullRefresh(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
    }

    public CustomSwipeRefreshLayout setup(android.view.ViewGroup.LayoutParams layoutParams) {
        swipeRefreshLayout = new CustomSwipeRefreshLayout(context);
        
        if (webView.getParent() != null) {
            ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        }

        swipeRefreshLayout.addView(webView, new CustomSwipeRefreshLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (layoutParams != null) {
            swipeRefreshLayout.setLayoutParams(layoutParams);
        }

        swipeRefreshLayout.setEnabled(true);

        webView.addJavascriptInterface(this, "PullRefreshBridge");

        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 1500);
        });

        return swipeRefreshLayout;
    }

    @JavascriptInterface
    public void updateScrollState(boolean atTop) {
        if (swipeRefreshLayout != null) {
            swipeRefreshLayout.setAtTop(atTop);
        }
    }

    public void injectScrollDetector() {
        String script = "(function() {" +
                "  if (window.pullRefreshInjected) return;" +
                "  window.pullRefreshInjected = true;" +
                "  " +
                "  window.addEventListener('touchstart', function(e) {" +
                "    if (e.touches.length !== 1) return;" +
                "    let touchX = e.touches[0].clientX;" +
                "    let touchY = e.touches[0].clientY;" +
                "    " +
                "    let atTop = true;" +
                "    let target = document.elementFromPoint(touchX, touchY);" +
                "    " +
                "    while (target && target !== document.body && target !== document.documentElement) {" +
                "      let style = window.getComputedStyle(target);" +
                "      let overflowY = style.getPropertyValue('overflow-y');" +
                "      let overflow = style.getPropertyValue('overflow');" +
                "      " +
                "      if (overflowY === 'auto' || overflowY === 'scroll' || overflow === 'auto' || overflow === 'scroll') {" +
                "        if (target.scrollTop > 0) {" +
                "          atTop = false;" +
                "          break;" +
                "        }" +
                "      }" +
                "      target = target.parentElement;" +
                "    }" +
                "    " +
                "    if (atTop) {" +
                "      let pageScroll = window.pageYOffset || document.documentElement.scrollTop;" +
                "      atTop = (pageScroll <= 0);" +
                "    }" +
                "    " +
                "    if (window.PullRefreshBridge) {" +
                "      window.PullRefreshBridge.updateScrollState(atTop);" +
                "    }" +
                "  }, { passive: true });" +
                "})();";

        webView.evaluateJavascript(script, null);
    }

    public static class CustomSwipeRefreshLayout extends SwipeRefreshLayout {
        private float startX = 0f;
        private float startY = 0f;
        private boolean isAtTop = true;

        public CustomSwipeRefreshLayout(Context context) {
            super(context);
        }

        public void setAtTop(boolean atTop) {
            this.isAtTop = atTop;
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = ev.getX();
                    startY = ev.getY();
                    break;
                    
                case MotionEvent.ACTION_MOVE:
                    float diffY = ev.getY() - startY;
                    float diffX = Math.abs(ev.getX() - startX);

                    // Ako je pokret horizontalniji ili ide nagore, ili ako nismo na vrhu, 
                    // ili ako pomeraj nije veći od 120 piksela -> APSOLUTNO ZABRANJUJEMO REFRESH
                    if (diffY < 120 || !isAtTop || diffX > diffY) {
                        return false; 
                    }
                    break;
            }
            return super.onInterceptTouchEvent(ev);
        }
    }
}
