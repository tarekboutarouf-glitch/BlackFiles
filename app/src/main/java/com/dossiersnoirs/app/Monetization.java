package com.dossiersnoirs.app;

import android.app.Activity;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import android.webkit.WebView;

import androidx.annotation.NonNull;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.gms.ads.rewarded.RewardedAd;
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Publicités (AdMob) + achat unique « Supprimer les pubs » (Google Play Billing).
 * Le jeu (page web) appelle ces méthodes via le pont JavaScript « BFNative ».
 * Les réponses sont renvoyées au jeu par window.bfNativeCb(type, valeur).
 */
public class Monetization {
    private final Activity act;
    private final WebView web;
    private final FrameLayout bannerBox;
    private final SharedPreferences prefs;
    private final AtomicBoolean adsStarted = new AtomicBoolean(false);
    private ConsentInformation consent;
    private RewardedAd rewarded;
    private InterstitialAd interstitial;
    private AdView banner;
    private boolean bannerWanted = false;
    private BillingClient billing;
    private ProductDetails removeAds;

    Monetization(Activity a, WebView w, FrameLayout box) {
        act = a; web = w; bannerBox = box;
        prefs = a.getSharedPreferences("bf", Activity.MODE_PRIVATE);
    }

    boolean noAds() { return prefs.getBoolean("noads", false); }

    void cb(String type, String value) {
        String v = value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
        act.runOnUiThread(() -> web.evaluateJavascript("window.bfNativeCb&&bfNativeCb('" + type + "','" + v + "')", null));
    }

    // ---------- démarrage : consentement (UE/RU) puis publicités, et facturation ----------
    void start() {
        startBilling();
        ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
        consent = UserMessagingPlatform.getConsentInformation(act);
        consent.requestConsentInfoUpdate(act, params,
            () -> UserMessagingPlatform.loadAndShowConsentFormIfRequired(act, err -> {
                if (consent.canRequestAds()) initAds();
            }),
            err -> { cb("aderr", "consent " + err.getMessage()); initAds(); });
        if (consent.canRequestAds()) initAds();
    }

    private void initAds() {
        if (adsStarted.getAndSet(true)) return;
        new Thread(() -> {
            MobileAds.initialize(act, s -> {});
            act.runOnUiThread(() -> { loadRewarded(); if (!noAds()) loadInterstitial(); if (bannerWanted) setBanner(true); });
        }).start();
    }

    // ---------- vidéo récompensée (indices 2 et 3) ----------
    private void loadRewarded() {
        RewardedAd.load(act, act.getString(R.string.ad_rewarded), new AdRequest.Builder().build(), new RewardedAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull RewardedAd ad) { rewarded = ad; }
            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { rewarded = null; cb("aderr", "rewarded " + e.getCode() + " " + e.getMessage()); }
        });
    }

    void showRewarded() {
        act.runOnUiThread(() -> {
            if (noAds()) { cb("reward", ""); return; }
            if (rewarded == null) { cb("noad", ""); loadRewarded(); return; }
            final boolean[] earned = {false};
            rewarded.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdDismissedFullScreenContent() { rewarded = null; loadRewarded(); cb(earned[0] ? "reward" : "cancel", ""); }
                @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { rewarded = null; loadRewarded(); cb("noad", ""); }
            });
            rewarded.show(act, item -> earned[0] = true);
        });
    }

    // ---------- interstitiel (après la résolution de certaines affaires) ----------
    private void loadInterstitial() {
        InterstitialAd.load(act, act.getString(R.string.ad_interstitial), new AdRequest.Builder().build(), new InterstitialAdLoadCallback() {
            @Override public void onAdLoaded(@NonNull InterstitialAd ad) { interstitial = ad; }
            @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { interstitial = null; cb("aderr", "interstitial " + e.getCode() + " " + e.getMessage()); }
        });
    }

    void showInterstitial() {
        act.runOnUiThread(() -> {
            if (noAds() || interstitial == null) return;
            interstitial.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override public void onAdDismissedFullScreenContent() { interstitial = null; loadInterstitial(); }
                @Override public void onAdFailedToShowFullScreenContent(@NonNull AdError e) { interstitial = null; loadInterstitial(); }
            });
            interstitial.show(act);
        });
    }

    // ---------- bannière (seulement sur la carte des affaires) ----------
    void setBanner(boolean on) {
        act.runOnUiThread(() -> {
            bannerWanted = on;
            if (!on || noAds() || !adsStarted.get()) { bannerBox.setVisibility(View.GONE); return; }
            if (banner == null) {
                banner = new AdView(act);
                banner.setAdUnitId(act.getString(R.string.ad_banner));
                DisplayMetrics dm = act.getResources().getDisplayMetrics();
                int w = (int) (dm.widthPixels / dm.density);
                banner.setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(act, w));
                bannerBox.addView(banner);
                banner.setAdListener(new com.google.android.gms.ads.AdListener() {
                    @Override public void onAdFailedToLoad(@NonNull LoadAdError e) { cb("aderr", "banner " + e.getCode() + " " + e.getMessage()); }
                });
                banner.loadAd(new AdRequest.Builder().build());
            }
            bannerBox.setVisibility(View.VISIBLE);
        });
    }

    // ---------- achat « Supprimer les pubs » ----------
    private void startBilling() {
        billing = BillingClient.newBuilder(act)
            .setListener((res, purchases) -> {
                if (res.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) handle(purchases);
                else if (res.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) restore();
                else cb("buyfail", "");
            })
            .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
            .build();
        billing.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(@NonNull BillingResult r) {
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                restore();
                QueryProductDetailsParams q = QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(
                    QueryProductDetailsParams.Product.newBuilder().setProductId(act.getString(R.string.iap_remove_ads))
                        .setProductType(BillingClient.ProductType.INAPP).build())).build();
                billing.queryProductDetailsAsync(q, (br, result) -> {
                    List<ProductDetails> l = result.getProductDetailsList();
                    if (l != null && !l.isEmpty()) {
                        removeAds = l.get(0);
                        ProductDetails.OneTimePurchaseOfferDetails o = removeAds.getOneTimePurchaseOfferDetails();
                        if (o != null) cb("price", o.getFormattedPrice());
                    }
                });
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    private void restore() {
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
            (r, list) -> { if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) handle(list); });
    }

    private void handle(List<Purchase> list) {
        for (Purchase p : list) {
            if (!p.getProducts().contains(act.getString(R.string.iap_remove_ads))) continue;
            if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) continue;
            prefs.edit().putBoolean("noads", true).apply();
            setBanner(false);
            cb("noads", "1");
            if (!p.isAcknowledged())
                billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), r -> {});
        }
    }

    void buyRemoveAds() {
        act.runOnUiThread(() -> {
            if (removeAds == null) { cb("buyfail", "unavailable"); return; }
            BillingFlowParams f = BillingFlowParams.newBuilder().setProductDetailsParamsList(Collections.singletonList(
                BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(removeAds).build())).build();
            billing.launchBillingFlow(act, f);
        });
    }

    String price() {
        if (removeAds == null || removeAds.getOneTimePurchaseOfferDetails() == null) return "";
        return removeAds.getOneTimePurchaseOfferDetails().getFormattedPrice();
    }

    /** Pont appelé depuis le JavaScript du jeu (window.BFNative). */
    public class Bridge {
        @android.webkit.JavascriptInterface public boolean noAds() { return Monetization.this.noAds(); }
        @android.webkit.JavascriptInterface public String price() { return Monetization.this.price(); }
        @android.webkit.JavascriptInterface public void showRewarded() { Monetization.this.showRewarded(); }
        @android.webkit.JavascriptInterface public void showInterstitial() { Monetization.this.showInterstitial(); }
        @android.webkit.JavascriptInterface public void setBanner(boolean on) { Monetization.this.setBanner(on); }
        @android.webkit.JavascriptInterface public void buyRemoveAds() { Monetization.this.buyRemoveAds(); }
    }
}
