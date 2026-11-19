package com.example.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAd
import com.google.android.gms.ads.rewardedinterstitial.RewardedInterstitialAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class AdMobManager(context: Context) {
    companion object {
        const val APP_ID = "ca-app-pub-1069125783691383~1918252053"
        const val REWARDED_AD_UNIT_ID = "ca-app-pub-1069125783691383/4387347779"
        const val REWARDED_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1069125783691383/3046469651"
        const val INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-1069125783691383/7905586656"

        private const val RETRY_INTERVAL_MS = 8_000L
        private const val EXPORT_AD_MAX_WAIT_MS = 3_000L
    }

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    private var isInitialized = false

    @Volatile
    private var rewardedAd: RewardedAd? = null
    @Volatile
    private var isLoadingRewarded = false

    @Volatile
    private var rewardedInterstitialAd: RewardedInterstitialAd? = null
    @Volatile
    private var isLoadingRewardedInterstitial = false

    @Volatile
    private var interstitialAd: InterstitialAd? = null
    @Volatile
    private var isLoadingInterstitial = false

    fun initializeAndPreload() {
        if (isInitialized) return
        isInitialized = true

        runCatching {
            MobileAds.initialize(appContext) {
                preloadAllTiers()
            }
        }.onFailure {
            // Silently handle environments without Play Services
        }

        // Pre-load immediately and retry every 8 seconds if any tier isn't ready
        preloadAllTiers()
        scope.launch {
            while (isActive) {
                delay(RETRY_INTERVAL_MS)
                preloadAllTiers()
            }
        }
    }

    fun preloadAllTiers() {
        mainHandler.post {
            loadRewarded()
            loadRewardedInterstitial()
            loadInterstitial()
        }
    }

    private fun loadRewarded() {
        if (rewardedAd != null || isLoadingRewarded) return
        isLoadingRewarded = true
        runCatching {
            val request = AdRequest.Builder().build()
            RewardedAd.load(
                appContext,
                REWARDED_AD_UNIT_ID,
                request,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isLoadingRewarded = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedAd = null
                        isLoadingRewarded = false
                    }
                }
            )
        }.onFailure {
            isLoadingRewarded = false
        }
    }

    private fun loadRewardedInterstitial() {
        if (rewardedInterstitialAd != null || isLoadingRewardedInterstitial) return
        isLoadingRewardedInterstitial = true
        runCatching {
            val request = AdRequest.Builder().build()
            RewardedInterstitialAd.load(
                appContext,
                REWARDED_INTERSTITIAL_AD_UNIT_ID,
                request,
                object : RewardedInterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedInterstitialAd) {
                        rewardedInterstitialAd = ad
                        isLoadingRewardedInterstitial = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        rewardedInterstitialAd = null
                        isLoadingRewardedInterstitial = false
                    }
                }
            )
        }.onFailure {
            isLoadingRewardedInterstitial = false
        }
    }

    private fun loadInterstitial() {
        if (interstitialAd != null || isLoadingInterstitial) return
        isLoadingInterstitial = true
        runCatching {
            val request = AdRequest.Builder().build()
            InterstitialAd.load(
                appContext,
                INTERSTITIAL_AD_UNIT_ID,
                request,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        isLoadingInterstitial = false
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        isLoadingInterstitial = false
                    }
                }
            )
        }.onFailure {
            isLoadingInterstitial = false
        }
    }

    /**
     * Triggers Priority 1 (Rewarded) -> Priority 2 (Rewarded Interstitial) -> Priority 3 (Interstitial)
     * with a strict 3-second max wait so Export never hangs or gets stuck.
     */
    suspend fun showExportAdWithTimeout(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return

        // If nothing is pre-loaded right now, trigger load & wait up to 3 seconds max
        if (rewardedAd == null && rewardedInterstitialAd == null && interstitialAd == null) {
            preloadAllTiers()
            withTimeoutOrNull(EXPORT_AD_MAX_WAIT_MS) {
                while (rewardedAd == null && rewardedInterstitialAd == null && interstitialAd == null) {
                    delay(120L)
                }
            }
        }

        // Strict priority cascade: Tier 1 -> Tier 2 -> Tier 3
        withContext(Dispatchers.Main.immediate) {
            val p1 = rewardedAd
            if (p1 != null) {
                rewardedAd = null
                val shown = showRewardedAd(activity, p1)
                preloadAllTiers()
                if (shown) return@withContext
            }

            val p2 = rewardedInterstitialAd
            if (p2 != null) {
                rewardedInterstitialAd = null
                val shown = showRewardedInterstitialAd(activity, p2)
                preloadAllTiers()
                if (shown) return@withContext
            }

            val p3 = interstitialAd
            if (p3 != null) {
                interstitialAd = null
                showInterstitialAd(activity, p3)
                preloadAllTiers()
            }
        }
    }

    private suspend fun showRewardedAd(activity: Activity, ad: RewardedAd): Boolean =
        suspendCancellableCoroutine { cont ->
            var resumed = false
            fun finish(ok: Boolean) {
                if (!resumed && cont.isActive) {
                    resumed = true
                    cont.resume(ok)
                }
            }

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    finish(true)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    finish(false)
                }
            }

            runCatching {
                ad.show(activity) { /* reward earned */ }
            }.onFailure {
                finish(false)
            }
        }

    private suspend fun showRewardedInterstitialAd(
        activity: Activity,
        ad: RewardedInterstitialAd
    ): Boolean = suspendCancellableCoroutine { cont ->
        var resumed = false
        fun finish(ok: Boolean) {
            if (!resumed && cont.isActive) {
                resumed = true
                cont.resume(ok)
            }
        }

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                finish(true)
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                finish(false)
            }
        }

        runCatching {
            ad.show(activity) { /* reward earned */ }
        }.onFailure {
            finish(false)
        }
    }

    private suspend fun showInterstitialAd(activity: Activity, ad: InterstitialAd): Boolean =
        suspendCancellableCoroutine { cont ->
            var resumed = false
            fun finish(ok: Boolean) {
                if (!resumed && cont.isActive) {
                    resumed = true
                    cont.resume(ok)
                }
            }

            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    finish(true)
                }

                override fun onAdFailedToShowFullScreenContent(error: AdError) {
                    finish(false)
                }
            }

            runCatching {
                ad.show(activity)
            }.onFailure {
                finish(false)
            }
        }
}
