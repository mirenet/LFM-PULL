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

    /**
     * Injektujemo JS skriptu koja proverava element TAČNO na mestu gde je prst spušten
     */
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
                "    // Proveravamo stablo od elementa na koji je prst spuštен nagore" +
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
                "    // Ako su unutrašnji kontejneri na vrhu, proveravamo i glavni dokument" +
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
        private float startY = 0f;
        private boolean isDraggingToRefresh = false;
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
                    startY = ev.getY();
                    isDraggingToRefresh = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    float diffY = ev.getY() - startY;
                    
                    // Uslov: Prst mora biti na vrhu elementa (isAtTop), povučen više od 120px nadole
                    if (diffY > 120 && isAtTop && !canChildScrollUp()) {
                        isDraggingToRefresh = true;
                    } else if (!isDraggingToRefresh) {
                        return false; 
                    }
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    isDraggingToRefresh = false;
                    break;
            }
            return super.onInterceptTouchEvent(ev);
        }
    }
}
