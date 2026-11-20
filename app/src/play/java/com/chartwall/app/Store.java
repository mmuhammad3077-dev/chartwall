package com.chartwall.app;

import android.app.Activity;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.Collections;
import java.util.List;

/** Google Play Billing: one-time "chartwall_pro" purchase that unlocks Pro. */
public final class Store implements PurchasesUpdatedListener {
    public static final String PRODUCT_ID = "chartwall_pro";

    public interface Listener {
        void onProChanged(boolean pro);
        void onPrice(String formattedPrice);
        void onMessage(String msg);
    }

    private final Activity activity;
    private final Listener listener;
    private final BillingClient client;
    private ProductDetails details;

    public Store(Activity activity, Listener listener) {
        this.activity = activity;
        this.listener = listener;
        client = BillingClient.newBuilder(activity)
                .setListener(this)
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .build();
    }

    public static boolean billingEnabled() { return true; }

    public void start() {
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult r) {
                if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                loadProduct();
                restore();
            }
            @Override public void onBillingServiceDisconnected() { }
        });
    }

    public void end() { client.endConnection(); }

    private void loadProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()))
                .build();
        client.queryProductDetailsAsync(params, (r, list) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK || list == null || list.isEmpty()) return;
            details = list.get(0);
            ProductDetails.OneTimePurchaseOfferDetails offer = details.getOneTimePurchaseOfferDetails();
            if (offer != null) {
                String price = offer.getFormattedPrice();
                activity.runOnUiThread(() -> listener.onPrice(price));
            }
        });
    }

    private void restore() {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (r, purchases) -> {
                    if (r.getResponseCode() != BillingClient.BillingResponseCode.OK) return;
                    boolean owned = false;
                    for (Purchase p : purchases) owned |= handle(p);
                    final boolean pro = owned;
                    activity.runOnUiThread(() -> {
                        Config.setPro(activity, pro);
                        listener.onProChanged(pro);
                    });
                });
    }

    public void buy() {
        if (details == null) {
            listener.onMessage("Pro abhi load nahi hua. Internet check karke dobara try karo.");
            if (client.isReady()) loadProduct(); else start();
            return;
        }
        BillingFlowParams params = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).build()))
                .build();
        client.launchBillingFlow(activity, params);
    }

    @Override
    public void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        int code = r.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            boolean owned = false;
            for (Purchase p : purchases) owned |= handle(p);
            if (owned) activity.runOnUiThread(() -> {
                Config.setPro(activity, true);
                listener.onProChanged(true);
                listener.onMessage("Shukriya! Pro unlock ho gaya.");
            });
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            restore();
        } else if (code != BillingClient.BillingResponseCode.USER_CANCELED) {
            activity.runOnUiThread(() -> listener.onMessage("Payment complete nahi hui. Dobara try karo."));
        }
    }

    /** Returns true when this purchase is a completed Pro purchase. Acknowledges it if needed. */
    private boolean handle(Purchase p) {
        if (!p.getProducts().contains(PRODUCT_ID)) return false;
        if (p.getPurchaseState() != Purchase.PurchaseState.PURCHASED) return false;
        if (!p.isAcknowledged()) {
            client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(p.getPurchaseToken()).build(), r -> { });
        }
        return true;
    }
}
