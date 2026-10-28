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
    private boolean isAtTop = true; // Keširano stanje sajt/element pozicije

    public PullRefresh(Context context, WebView webView) {
        this.context = context;
        this.webView = webView;
    }

    /**
     * Obavija prosleđeni WebView u SwipeRefreshLayout i postavlja JavaScript Bridge
     */
    public SwipeRefreshLayout setup(android.view.ViewGroup.LayoutParams layoutParams) {
        // 1. Kreiramo SwipeRefreshLayout
        swipeRefreshLayout = new SwipeRefreshLayout(context);
        
        // Uklanjamo WebView iz njegovog trenutnog roditelja ako postoji
        if (webView.getParent() != null) {
            ((android.view.ViewGroup) webView.getParent()).removeView(webView);
        }

        // 2. Ubacujemo WebView unutar SwipeRefreshLayout-a
        swipeRefreshLayout.addView(webView, new SwipeRefreshLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if (layoutParams != null) {
            swipeRefreshLayout.setLayoutParams(layoutParams);
        }

        // 3. Registrujemo JavaScript Bridge za praćenje pozicije skrola
        webView.addJavascriptInterface(this, "PullRefreshBridge");

        // 4. Konfigurišemo akciju kada korisnik pusti pull-to-refresh
        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
            // Osvežavanje traje kratko, pa gasimo indikator posle 1.5 sekunde ako se stranica ranije ne učita
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                if (swipeRefreshLayout != null) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }, 1500);
        });

        return swipeRefreshLayout;
    }

    /**
     * Metoda koju JavaScript zove u hodu kada se menja pozicija skrola
     */
    @JavascriptInterface
    public void updateScrollState(boolean atTop) {
        this.isAtTop = atTop;
        
        // Sinhronizujemo sa UI nitom da omogućimo/onemogućimo refresh kontejner
        new Handler(Looper.getMainLooper()).post(() -> {
            if (swipeRefreshLayout != null) {
                swipeRefreshLayout.setEnabled(atTop);
            }
        });
    }

    /**
     * Injektujemo JS skriptu koja proverava da li je stranica ili unutrašnji element na vrhu
     */
    public void injectScrollDetector() {
        String script = "(function() {" +
                "  if (window.pullRefreshInjected) return;" +
                "  window.pullRefreshInjected = true;" +
                "  " +
                "  window.addEventListener('scroll', function() {" +
                "    let scrollTop = window.pageYOffset || document.documentElement.scrollTop;" +
                "    let atTop = (scrollTop <= 0);" +
                "    if (window.PullRefreshBridge) {" +
                "      window.PullRefreshBridge.updateScrollState(atTop);" +
                "    }" +
                "  }, { passive: true });" +
                "  " +
                "  // Dodatna provera na dodir (za slučajeve sa unutrašnjim skrolom)" +
                "  window.addEventListener('touchstart', function(e) {" +
                "    let target = e.target;" +
                "    let atTop = true;" +
                "    while (target && target !== document.body) {" +
                "      let style = window.getComputedStyle(target);" +
                "      let overflowY = style.getPropertyValue('overflow-y');" +
                "      if ((overflowY === 'auto' || overflowY === 'scroll') && target.scrollHeight > target.clientHeight) {" +
                "        if (target.scrollTop > 0) {" +
                "          atTop = false;" +
                "          break;" +
                "        }" +
                "      }" +
                "      target = target.parentElement;" +
                "    }" +
                "    if (atTop) {" +
                "      let pageTop = (window.pageYOffset || document.documentElement.scrollTop) <= 0;" +
                "      atTop = pageTop;" +
                "    }" +
                "    if (window.PullRefreshBridge) {" +
                "      window.PullRefreshBridge.updateScrollState(atTop);" +
                "    }" +
                "  }, { passive: true });" +
                "})();";

        webView.evaluateJavascript(script, null);
    }
}
