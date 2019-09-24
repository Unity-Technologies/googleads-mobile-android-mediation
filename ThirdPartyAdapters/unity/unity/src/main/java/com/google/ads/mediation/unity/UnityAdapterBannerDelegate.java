package com.google.ads.mediation.unity;

//import com.unity3d.services.banners.IUnityBannerListener;
import com.unity3d.services.banners.BannerView;

public interface UnityAdapterBannerDelegate extends BannerView.IListener {
    String getPlacementId();
}
