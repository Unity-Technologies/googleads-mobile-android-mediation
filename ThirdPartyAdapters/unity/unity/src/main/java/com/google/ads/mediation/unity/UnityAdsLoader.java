package com.google.ads.mediation.unity;

import android.app.Activity;

import com.google.android.gms.ads.mediation.MediationAdConfiguration;
import com.unity3d.ads.IUnityAdsLoadListener;
import com.unity3d.ads.IUnityAdsShowListener;
import com.unity3d.ads.UnityAds;
import com.unity3d.ads.UnityAdsLoadOptions;
import com.unity3d.ads.UnityAdsShowOptions;

/** Wrapper class for {@link UnitAds#load} and {@link UnityAds#show} */
class UnityAdsLoader {
  private static final String WATERMARK_KEY = "watermark";

  public void load(
      String placementId,
      UnityAdsLoadOptions unityAdsLoadOptions,
      IUnityAdsLoadListener unityAdsLoadListener) {
    UnityAds.load(placementId, unityAdsLoadOptions, unityAdsLoadListener);
  }

  public void show(
      Activity activity,
      String placementId,
      UnityAdsShowOptions unityAdsShowOptions,
      IUnityAdsShowListener unityAdsShowListener) {
    UnityAds.show(activity, placementId, unityAdsShowOptions, unityAdsShowListener);
  }

  public UnityAdsLoadOptions createUnityAdsLoadOptionsWithId(String objectId) {
    UnityAdsLoadOptions unityAdsLoadOptions = new UnityAdsLoadOptions();
    unityAdsLoadOptions.setObjectId(objectId);
    return unityAdsLoadOptions;
  }

  public UnityAdsShowOptions createUnityAdsShowOptionsWithId(String objectId, MediationAdConfiguration adConfiguration) {
    UnityAdsShowOptions unityAdsShowOptions = new UnityAdsShowOptions();
    unityAdsShowOptions.setObjectId(objectId);
    unityAdsShowOptions.set(WATERMARK_KEY, adConfiguration.getWatermark());
    return unityAdsShowOptions;
  }
}
