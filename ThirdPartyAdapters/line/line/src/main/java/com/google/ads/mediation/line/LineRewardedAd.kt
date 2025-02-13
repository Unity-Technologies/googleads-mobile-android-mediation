// Copyright 2023 Google LLC
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

package com.google.ads.mediation.line

import android.app.Activity
import android.content.Context
import android.util.Log
import com.five_corp.ad.FiveAdErrorCode
import com.five_corp.ad.FiveAdInterface
import com.five_corp.ad.FiveAdLoadListener
import com.five_corp.ad.FiveAdState
import com.five_corp.ad.FiveAdVideoReward
import com.five_corp.ad.FiveAdViewEventListener
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.mediation.MediationAdLoadCallback
import com.google.android.gms.ads.mediation.MediationRewardedAd
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration
import com.google.android.gms.ads.rewarded.RewardItem
import java.lang.ref.WeakReference

/**
 * Used to load Line rewarded ads and mediate callbacks between Google Mobile Ads SDK and FiveAd
 * SDK.
 */
class LineRewardedAd
private constructor(
  private val activityReference: WeakReference<Activity>,
  private val appId: String,
  private val mediationAdLoadCallback:
    MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
  private val rewardedAd: FiveAdVideoReward,
) : MediationRewardedAd, FiveAdLoadListener, FiveAdViewEventListener {

  private var mediationRewardedAdCallback: MediationRewardedAdCallback? = null

  fun loadAd() {
    val activity = activityReference.get() ?: return
    LineInitializer.initialize(activity, appId)
    rewardedAd.setLoadListener(this)
    rewardedAd.loadAdAsync()
  }

  override fun showAd(context: Context) {
    val activity = activityReference.get() ?: return
    val showedFullscreen = rewardedAd.show(activity)
    if (!showedFullscreen) {
      val adError =
        AdError(
          LineMediationAdapter.ERROR_CODE_FAILED_TO_SHOW_FULLSCREEN,
          LineMediationAdapter.ERROR_MSG_FAILED_TO_SHOW_FULLSCREEN,
          LineMediationAdapter.SDK_ERROR_DOMAIN
        )
      Log.w(TAG, adError.message)
      mediationRewardedAdCallback?.onAdFailedToShow(adError)
      return
    }
    mediationRewardedAdCallback?.onAdOpened()
  }

  override fun onFiveAdLoad(ad: FiveAdInterface) {
    Log.d(TAG, "Finished loading Line Rewarded Ad for slotId: ${ad.slotId}")
    mediationRewardedAdCallback = mediationAdLoadCallback.onSuccess(this)
    rewardedAd.setViewEventListener(this)
  }

  override fun onFiveAdLoadError(ad: FiveAdInterface, errorCode: FiveAdErrorCode) {
    val adError =
      AdError(
        errorCode.value,
        String.format(LineMediationAdapter.ERROR_MSG_AD_LOADING, errorCode.name),
        LineMediationAdapter.SDK_ERROR_DOMAIN
      )
    Log.w(TAG, adError.message)
    mediationAdLoadCallback.onFailure(adError)
  }

  override fun onFiveAdViewError(ad: FiveAdInterface, errorCode: FiveAdErrorCode) {
    val adError =
      AdError(
        errorCode.value,
        String.format(LineMediationAdapter.ERROR_MSG_AD_SHOWING, errorCode.name),
        LineMediationAdapter.SDK_ERROR_DOMAIN
      )
    Log.w(TAG, adError.message)
    mediationRewardedAdCallback?.onAdFailedToShow(adError)
  }

  override fun onFiveAdClick(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad did record a click.")
    mediationRewardedAdCallback?.reportAdClicked()
  }

  override fun onFiveAdClose(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad closed")
    mediationRewardedAdCallback?.apply {
      onAdClosed()
      if (ad.state != FiveAdState.ERROR) {
        Log.d(TAG, "Line reward earned")
        onUserEarnedReward(LineRewardItem())
      }
    }
  }

  override fun onFiveAdStart(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad start")
    mediationRewardedAdCallback?.onVideoStart()
  }

  override fun onFiveAdPause(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad paused")
    // Google Mobile Ads SDK doesn't have a matching event.
  }

  override fun onFiveAdResume(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad resumed")
    // Google Mobile Ads SDK doesn't have a matching event.
  }

  override fun onFiveAdViewThrough(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded video ad viewed")
    mediationRewardedAdCallback?.onVideoComplete()
  }

  override fun onFiveAdReplay(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad replayed")
    // Google Mobile Ads SDK doesn't have a matching event.
  }

  override fun onFiveAdImpression(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad recorded an impression.")
    mediationRewardedAdCallback?.reportAdImpression()
  }

  override fun onFiveAdStall(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad stalled")
    // Google Mobile Ads SDK doesn't have a matching event.
  }

  override fun onFiveAdRecover(ad: FiveAdInterface) {
    Log.d(TAG, "Line rewarded ad recovered")
    // Google Mobile Ads SDK doesn't have a matching event.
  }

  class LineRewardItem : RewardItem {
    override fun getAmount(): Int = 1

    override fun getType(): String = ""
  }

  companion object {
    private val TAG = LineRewardedAd::class.simpleName

    fun newInstance(
      mediationRewardedAdConfiguration: MediationRewardedAdConfiguration,
      mediationAdLoadCallback:
        MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback>,
    ): Result<LineRewardedAd> {
      val activity = mediationRewardedAdConfiguration.context as? Activity
      if (activity == null) {
        val adError =
          AdError(
            LineMediationAdapter.ERROR_CODE_CONTEXT_NOT_AN_ACTIVITY,
            LineMediationAdapter.ERROR_MSG_CONTEXT_NOT_AN_ACTIVITY,
            LineMediationAdapter.ADAPTER_ERROR_DOMAIN
          )
        mediationAdLoadCallback.onFailure(adError)
        return Result.failure(NoSuchElementException(adError.message))
      }
      val serverParameters = mediationRewardedAdConfiguration.serverParameters
      val appId = serverParameters.getString(LineMediationAdapter.KEY_APP_ID)
      if (appId.isNullOrEmpty()) {
        val adError =
          AdError(
            LineMediationAdapter.ERROR_CODE_MISSING_APP_ID,
            LineMediationAdapter.ERROR_MSG_MISSING_APP_ID,
            LineMediationAdapter.ADAPTER_ERROR_DOMAIN
          )
        mediationAdLoadCallback.onFailure(adError)
        return Result.failure(NoSuchElementException(adError.message))
      }

      val slotId = serverParameters.getString(LineMediationAdapter.KEY_SLOT_ID)
      if (slotId.isNullOrEmpty()) {
        val adError =
          AdError(
            LineMediationAdapter.ERROR_CODE_MISSING_SLOT_ID,
            LineMediationAdapter.ERROR_MSG_MISSING_SLOT_ID,
            LineMediationAdapter.ADAPTER_ERROR_DOMAIN
          )
        mediationAdLoadCallback.onFailure(adError)
        return Result.failure(NoSuchElementException(adError.message))
      }

      val fiveAdVideoRewarded = LineSdkFactory.delegate.createFiveVideoRewarded(activity, slotId)

      return Result.success(
        LineRewardedAd(WeakReference(activity), appId, mediationAdLoadCallback, fiveAdVideoRewarded)
      )
    }
  }
}
