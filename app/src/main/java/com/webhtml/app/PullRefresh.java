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
        // Koristimo našu custom klasu za filtriranje pokreta
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

        // Uvek je omogućen, ali Custom klasa unutra filtrira da li se aktivira na osnovu praga i pozicije
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
     * Injektujemo laganu JS skriptu koja samo javlja Androidu da li smo na vrhu stranice/elementa
     */
    public void injectScrollDetector() {
        String script = "(function() {" +
                "  if (window.pullRefreshInjected) return;" +
                "  window.pullRefreshInjected = true;" +
                "  " +
                "  function checkTop() {" +
                "    let scrollTop = window.pageYOffset || document.documentElement.scrollTop;" +
                "    let atTop = (scrollTop <= 0);" +
                "    if (atTop) {" +
                "      let target = document.elementFromPoint(window.innerWidth / 2, 50);" +
                "      while (target && target !== document.body) {" +
                "        let style = window.getComputedStyle(target);" +
                "        let overflowY = style.getPropertyValue('overflow-y');" +
                "        if ((overflowY === 'auto' || overflowY === 'scroll') && target.scrollHeight > target.clientHeight) {" +
                "          if (target.scrollTop > 0) {" +
                "            atTop = false;" +
                "            break;" +
                "          }" +
                "        }" +
                "        target = target.parentElement;" +
                "      }" +
                "    }" +
                "    if (window.PullRefreshBridge) {" +
                "      window.PullRefreshBridge.updateScrollState(atTop);" +
                "    }" +
                "  }" +
                "  " +
                "  window.addEventListener('scroll', checkTop, { passive: true });" +
                "  window.addEventListener('touchstart', checkTop, { passive: true });" +
                "})();";

        webView.evaluateJavascript(script, null);
    }

    /**
     * Unutrašnja Custom klasa koja rešava problem sa kratkim pokretima i pragom povlačenja
     */
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
                    
                    // USLOV: Mora da bude povučeno više od 120 piksela nadole, 
                    // da budemo na vrhu stranice i da WebView ne može više nagore.
                    if (diffY > 120 && isAtTop && !canChildScrollUp()) {
                        isDraggingToRefresh = true;
                    } else if (!isDraggingToRefresh) {
                        // Ako uslov nije zadovoljen, sprečavamo refresh da se aktivira na kratke pokrete
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
