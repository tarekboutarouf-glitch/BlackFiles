package com.dossiersnoirs.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.window.OnBackInvokedDispatcher;

public class MainActivity extends Activity {
    private WebView web;
    private Monetization money;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        web = new WebView(this);
        web.setBackgroundColor(Color.parseColor("#0d0b09"));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                 // sauvegarde de la progression
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient()); // confirm()
        web.setOverScrollMode(WebView.OVER_SCROLL_NEVER);

        // Android 15+ : affichage bord à bord -> on évite les barres système
        // Mise en page : le jeu en haut, la bannière (cachée par défaut) en bas
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#0d0b09"));
        root.addView(web, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        FrameLayout bannerBox = new FrameLayout(this);
        bannerBox.setVisibility(android.view.View.GONE);
        root.addView(bannerBox, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        money = new Monetization(this, web, bannerBox);
        web.addJavascriptInterface(money.new Bridge(), "BFNative");

        if (Build.VERSION.SDK_INT >= 30) {
            root.setOnApplyWindowInsetsListener((v, ins) -> {
                android.graphics.Insets i = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                v.setPadding(i.left, i.top, i.right, i.bottom);
                return WindowInsets.CONSUMED;
            });
        }
        setContentView(root);
        if (b != null) web.restoreState(b); else web.loadUrl("file:///android_asset/index.html");

        // Rappel quotidien (Android 13+ : demander l'autorisation des notifications)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS") != android.content.pm.PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 1);
        DailyReminder.schedule(this);

        // Publicités + achat « Supprimer les pubs »
        money.start();

        // Bouton retour (Android 13+ et Android 16)
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private void handleBack() {
        web.evaluateJavascript("window.dnBack?window.dnBack():false", r -> {
            if (!"true".equals(r)) finish();
        });
    }

    @Override public void onBackPressed() { handleBack(); }   // Android 12 et moins

    @Override protected void onSaveInstanceState(Bundle o) { super.onSaveInstanceState(o); web.saveState(o); }
}
