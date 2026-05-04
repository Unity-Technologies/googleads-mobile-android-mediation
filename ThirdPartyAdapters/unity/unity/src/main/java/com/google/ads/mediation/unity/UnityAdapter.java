// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.ads.mediation.unity;

import static com.google.ads.mediation.unity.UnityAdsAdapterUtils.AdEvent;
import static com.google.ads.mediation.unity.UnityAdsAdapterUtils.createAdError;
import static com.google.ads.mediation.unity.UnityAdsAdapterUtils.createSDKLoadError;
import static com.google.ads.mediation.unity.UnityAdsAdapterUtils.createSDKShowError;
import static com.google.ads.mediation.unity.UnityAdsAdapterUtils.getMediationInfo;
import static com.google.ads.mediation.unity.UnityAdsAdapterUtils.setUnityAdsPrivacy;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;

import com.google.ads.mediation.unity.eventadapters.UnityInterstitialEventAdapter;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.MediationInterstitialAdapter;
import com.google.android.gms.ads.mediation.MediationInterstitialListener;
import com.unity3d.ads.InterstitialAd;
import com.unity3d.ads.InterstitialShowListener;
import com.unity3d.ads.LoadConfiguration;
import com.unity3d.ads.LoadListener;
import com.unity3d.ads.ShowConfiguration;
import com.unity3d.ads.ShowFinishState;
import com.unity3d.ads.UnityAdsError;
import com.unity3d.ads.UnityAdsExperimental;
import java.lang.ref.WeakReference;

/**
 * The {@link UnityAdapter} is used to load Unity ads and mediate the callbacks between Google
 * Mobile Ads SDK and Unity Ads SDK.
 */
@Keep
@OptIn(markerClass = UnityAdsExperimental.class)
public class UnityAdapter extends UnityMediationAdapter implements MediationInterstitialAdapter {

  /**
   * Mediation interstitial listener used to forward events to Google Mobile Ads SDK.
   */
  private MediationInterstitialListener mediationInterstitialListener;

  /**
   * Placement ID used to determine what type of ad to load.
   */
  private String placementId;

  /**
   * InterstitialAd to keep reference of loaded ad.
   */
  private InterstitialAd loadedInterstitalAd;

  /**
   * An Android {@link Activity} weak reference used to show ads.
   */
  private WeakReference<Activity> activityWeakReference;

  /**
   * UnityInterstitialEventAdapter instance to send events from the mediationInterstitialListener.
   */
  private UnityInterstitialEventAdapter eventAdapter;

  /**
   * IUnityAdsLoadListener instance.
   */
  private final LoadListener<InterstitialAd> unityLoadListener = new LoadListener<>() {
    @Override
    public void onAdLoaded(@Nullable InterstitialAd interstitialAd, @Nullable UnityAdsError error) {
      if (error == null) {
        String logMessage = String.format(
                "Unity Ads interstitial ad successfully loaded for placement ID: %s", placementId);
        Log.d(TAG, logMessage);
        eventAdapter.sendAdEvent(AdEvent.LOADED);
        loadedInterstitalAd = interstitialAd;
      } else {
        AdError loadError = createSDKLoadError(error, error.getMessage());
        Log.w(TAG, loadError.toString());
        if (mediationInterstitialListener != null) {
          mediationInterstitialListener.onAdFailedToLoad(UnityAdapter.this, loadError);
        }
      }
    }
  };

  @Override
  public void requestInterstitialAd(@NonNull Context context,
      @NonNull MediationInterstitialListener mediationInterstitialListener,
      @NonNull Bundle serverParameters, @NonNull MediationAdRequest mediationAdRequest,
      @Nullable Bundle mediationExtras) {
    this.mediationInterstitialListener = mediationInterstitialListener;
    eventAdapter = new UnityInterstitialEventAdapter(this.mediationInterstitialListener, this);

    final String gameId = serverParameters.getString(KEY_GAME_ID);
    placementId = serverParameters.getString(KEY_PLACEMENT_ID);
    if (!UnityAdsAdapterUtils.areValidIds(gameId, placementId)) {
      sendAdFailedToLoad(ERROR_INVALID_SERVER_PARAMETERS, "Missing or invalid server parameters.");
      return;
    }

    if (!(context instanceof Activity)) {
      sendAdFailedToLoad(ERROR_CONTEXT_NOT_ACTIVITY,
          "Unity Ads requires an Activity context to load ads.");
      return;
    }
    Activity activity = (Activity) context;
    activityWeakReference = new WeakReference<>(activity);

    UnityInitializer.getInstance()
        .initializeUnityAds(gameId, error -> {
          if(error == null) {
            String logMessage = String.format("Unity Ads is initialized for game ID '%s' "
                + "and can now load interstitial ad with placement ID: %s", gameId, placementId);
            Log.d(TAG, logMessage);

            return;
          }

          String adErrorMessage = String.format(
              "Unity Ads initialization failed for game ID '%s' with error message: %s", gameId,
              error.getMessage());
          AdError adError = UnityAdsAdapterUtils.createSDKInitializationError(error, adErrorMessage);
          Log.w(TAG, adError.toString());

          if (UnityAdapter.this.mediationInterstitialListener != null) {
            UnityAdapter.this.mediationInterstitialListener.onAdFailedToLoad(UnityAdapter.this,
                adError);
          }
        });

    setUnityAdsPrivacy(MobileAds.getRequestConfiguration());

    LoadConfiguration loadConfiguration = new LoadConfiguration.Builder(placementId)
            .withMediationInfo(getMediationInfo())
            .build();
    InterstitialAd.load(loadConfiguration, unityLoadListener);
  }

  private void sendAdFailedToLoad(int errorCode, String errorDescription) {
    AdError adError = createAdError(errorCode, errorDescription);
    Log.w(TAG, adError.toString());
    if (mediationInterstitialListener != null) {
      mediationInterstitialListener.onAdFailedToLoad(UnityAdapter.this, adError);
    }
  }

  /**
   * This method shows a Unity interstitial ad.
   */
  @Override
  public void showInterstitial() {
    Activity activityReference = activityWeakReference == null ? null : activityWeakReference.get();
    if (activityReference == null) {
      Log.w(TAG, "Failed to show interstitial ad for placement ID '" + placementId
          + "' from Unity Ads: Activity context is null.");
      eventAdapter.sendAdEvent(AdEvent.CLOSED);
      return;
    }

    if (loadedInterstitalAd == null) {
      Log.w(TAG, "Unity Ads received call to show before successfully loading an ad.");
    }

    ShowConfiguration showConfiguration = new ShowConfiguration.Builder().build();

    // UnityAds can handle a null placement ID so show is always called here.
    loadedInterstitalAd.show(activityReference, showConfiguration, unityShowListener);
  }

  /**
   * IUnityAdsShowListener instance. Contains logic for callbacks when showing ads.
   */
  private final InterstitialShowListener unityShowListener = new InterstitialShowListener() {

    @Override
    public void onStarted(InterstitialAd interstitialAd) {
      String logMessage = String.format("Unity Ads interstitial ad started for placement ID: %s",
          UnityAdapter.this.placementId);
      Log.d(TAG, logMessage);

      // Unity Ads does not have an "ad opened" callback.
      // Sending Ad Opened event when the video ad starts playing.
      eventAdapter.sendAdEvent(AdEvent.OPENED);
    }

    @Override
    public void onClicked(InterstitialAd interstitialAd) {
      String logMessage = String.format(
          "Unity Ads interstitial ad was clicked for placement ID: %s",
          UnityAdapter.this.placementId);
      Log.d(TAG, logMessage);

      // Unity Ads ad clicked.
      eventAdapter.sendAdEvent(AdEvent.CLICKED);

      // Unity Ads doesn't provide a "leaving application" event, so assuming that the
      // user is leaving the application when a click is received, forwarding an on ad
      // left application event.
      eventAdapter.sendAdEvent(AdEvent.LEFT_APPLICATION);
    }

    @Override
    public void onCompleted(InterstitialAd interstitialAd, @NonNull ShowFinishState showFinishState) {
      String logMessage = String.format(
          "Unity Ads interstitial ad finished playing for placement ID: %s",
          UnityAdapter.this.placementId);
      Log.d(TAG, logMessage);

      // Unity Ads ad closed.
      eventAdapter.sendAdEvent(AdEvent.CLOSED);
    }

    @Override
    public void onFailed(InterstitialAd interstitialAd, @NonNull UnityAdsError error) {
      // Unity Ads ad failed to show.
      AdError adError = createSDKShowError(error, error.getMessage());
      Log.w(TAG, adError.toString());

      eventAdapter.sendAdEvent(AdEvent.OPENED);
      eventAdapter.sendAdEvent(AdEvent.CLOSED);
    }
  };

  @Override
  public void onDestroy() {
    mediationInterstitialListener = null;
  }

  @Override
  public void onPause() {
  }

  @Override
  public void onResume() {
  }
}
