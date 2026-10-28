package com.webhtml.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class PullRefresh {

    private final Context context;
    private final WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;

    public PullRefresh(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
    }

    public SwipeRefreshLayout setup(android.view.ViewGroup.LayoutParams layoutParams) {
        swipeRefreshLayout = new SwipeRefreshLayout(context);
        
        if (webView.getParent() != null) {
            ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        }

        swipeRefreshLayout.addView(webView, new SwipeRefreshLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (layoutParams != null) {
            swipeRefreshLayout.setLayoutParams(layoutParams);
        }

        // Isključujemo ga po default-u; JavaScript će ga paliti samo kada prepozna pravi gest
        swipeRefreshLayout.setEnabled(false);

        webView.addJavascriptInterface(this, "PullRefreshBridge");

        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                    swipeRefreshLayout.setEnabled(false);
                }
            }, 1500);
        });

        return swipeRefreshLayout;
    }

    @JavascriptInterface
    public void setRefreshEnabled(boolean enabled) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (swipeRefreshLayout != null && !swipeRefreshLayout.isRefreshing()) {
                swipeRefreshLayout.setEnabled(enabled);
            }
        });
    }

    /**
     * Injektujemo naprednu JS skriptu koja meri dužinu i nameru pokreta prsta,
     * sprečavajući da se krug pojavi na kratke pokrete i običan skrol.
     */
    public void injectScrollDetector() {
        String script = "(function() {" +
                "  if (window.pullRefreshInjected) return;" +
                "  window.pullRefreshInjected = true;" +
                "  " +
                "  let startY = 0;" +
                "  let startX = 0;" +
                "  let isAtTop = false;" +
                "  " +
                "  window.addEventListener('touchstart', function(e) {" +
                "    if (e.touches.length !== 1) return;" +
                "    startY = e.touches[0].clientY;" +
                "    startX = e.touches[0].clientX;" +
                "    " +
                "    // Proveravamo da li je stranica ili element na vrhu" +
                "    let scrollTop = window.pageYOffset || document.documentElement.scrollTop;" +
                "    isAtTop = (scrollTop <= 0);" +
                "    " +
                "    if (isAtTop) {" +
                "      let target = e.target;" +
                "      while (target && target !== document.body) {" +
                "        let style = window.getComputedStyle(target);" +
                "        let overflowY = style.getPropertyValue('overflow-y');" +
                "        if ((overflowY === 'auto' || overflowY === 'scroll') && target.scrollHeight > target.clientHeight) {" +
                "          if (target.scrollTop > 0) {" +
                "            isAtTop = false;" +
                "            break;" +
                "          }" +
                "        }" +
                "        target = target.parentElement;" +
                "      }" +
                "    }" +
                "    " +
                "    // Inicijalno gasimo refresh dok ne vidimo nameru" +
                "    if (window.PullRefreshBridge) {" +
                "      window.PullRefreshBridge.setRefreshEnabled(false);" +
                "    }" +
                "  }, { passive: true });" +
                "  " +
                "  window.addEventListener('touchmove', function(e) {" +
                "    if (!isAtTop || e.touches.length !== 1) return;" +
                "    let currentY = e.touches[0].clientY;" +
                "    let diffY = currentY - startY;" +
                "    " +
                "    // USLOV: Korisnik mora da povuče nadole za više od 120 piksela sa zadržavanjem" +
                "    // čime se eliminišu kratki pokreti i običan skrol." +
                "    if (diffY > 120) {" +
                "      if (window.PullRefreshBridge) {" +
                "        window.PullRefreshBridge.setRefreshEnabled(true);" +
                "      }" +
                "    }" +
                "  }, { passive: true });" +
                "  " +
                "  window.addEventListener('touchend', function(e) {" +
                "    isAtTop = false;" +
                "    if (window.PullRefreshBridge) {" +
                "      window.PullRefreshBridge.setRefreshEnabled(false);" +
                "    }" +
                "  }, { passive: true });" +
                "})();";

        webView.evaluateJavascript(script, null);
    }
}
