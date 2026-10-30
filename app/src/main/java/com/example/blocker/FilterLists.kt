package com.example.blocker

/**
 * FilterLists provides domain rules and cosmetic selectors derived from reputable
 * ad-blocking standards (EasyList & EasyPrivacy).
 *
 * Subject to content blocking policies:
 * - Blocks advertising networks, behavioral tracking, analytic beacons, and tracking pixels.
 * - Protects core functionality by excluding essential CDNs and functional domains.
 */
object FilterLists {

    // High-confidence advertising hosts (EasyList based)
    val AD_DOMAINS: Set<String> = hashSetOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "pagead2.googlesyndication.com",
        "adservice.google.com",
        "pubmatic.com",
        "rubiconproject.com",
        "openx.net",
        "appnexus.com",
        "adnxs.com",
        "criteo.com",
        "criteo.net",
        "outbrain.com",
        "taboola.com",
        "mgid.com",
        "revcontent.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "adsterra.com",
        "exoclick.com",
        "trafficjunky.com",
        "amazon-adsystem.com",
        "media.net",
        "adcolony.com",
        "vungle.com",
        "unityads.unity3d.com",
        "chartboost.com",
        "ironsrc.com",
        "mopub.com",
        "smartadserver.com",
        "sovereign.io",
        "sovrn.com",
        "bidswitch.net",
        "indexexchange.com",
        "casale.com",
        "casalemedia.com",
        "yieldmo.com",
        "triplelift.com",
        "gumgum.com",
        "sharethrough.com",
        "teads.tv",
        "spotxchange.com",
        "spotx.tv",
        "adroll.com",
        "advertising.com",
        "exponential.com",
        "inmobi.com",
        "fyber.com",
        "admob.com",
        "adform.net",
        "moatads.com",
        "adsafeprotected.com",
        "iasds01.com",
        "flashtalking.com",
        "serving-sys.com",
        "adblade.com",
        "zergnet.com",
        "content.ad",
        "yahoosmallbusinessonline.com"
    )

    // High-confidence telemetry and user tracking hosts (EasyPrivacy based)
    val TRACKER_DOMAINS: Set<String> = hashSetOf(
        "google-analytics.com",
        "analytics.google.com",
        "scorecardresearch.com",
        "quantserve.com",
        "quantcount.com",
        "hotjar.com",
        "crazyegg.com",
        "fullstory.com",
        "mixpanel.com",
        "segment.com",
        "segment.io",
        "amplitude.com",
        "newrelic.com",
        "bugsnag.com",
        "sentry.io",
        "statcounter.com",
        "chartbeat.com",
        "optimizely.com",
        "mouseflow.com",
        "luckyorange.com",
        "inspectlet.com",
        "clicky.com",
        "histats.com",
        "clarity.ms",
        "bat.bing.com",
        "ads-twitter.com",
        "static.ads-twitter.com",
        "px.ads.linkedin.com",
        "snap.licdn.com",
        "connect.facebook.net/signals",
        "tr.snapchat.com",
        "sc-static.net/scevent.min.js"
    )

    // Keyword signatures in request paths that signal ads or tracking
    val BLOCKED_PATH_SIGNATURES: List<String> = listOf(
        "/pagead/",
        "/ads/delivery",
        "/adserver/",
        "/pixel.gif",
        "/tr?",
        "/collect?",
        "/beacon.min.js",
        "/telemetry/",
        "/analytics.js",
        "/gtag/js?id=AW-",
        "/gtag/js?id=DC-"
    )

    // Essential functional domains that must never be blocked even if matched
    val SAFE_HOST_ALLOWLIST: Set<String> = hashSetOf(
        "www.google.com",
        "google.com",
        "maps.google.com",
        "fonts.googleapis.com",
        "fonts.gstatic.com",
        "ajax.googleapis.com",
        "apis.google.com",
        "www.gstatic.com",
        "gstatic.com",
        "recaptcha.net",
        "www.recaptcha.net",
        "en.wikipedia.org",
        "wikipedia.org",
        "wikimedia.org",
        "github.com",
        "githubusercontent.com"
    )

    // Cosmetic CSS injection to collapse ad containers on the client DOM
    const val COSMETIC_CSS = """
        .adsbygoogle,
        [id^="google_ads_"],
        [id^="div-gpt-ad"],
        div[class*="-ad-banner"],
        div[class*="-advertisement"],
        .advertisement,
        .ad-container,
        [data-ad-slot],
        [data-ad-client],
        iframe[src*="doubleclick"],
        iframe[src*="adservice"],
        .taboola-placeholder,
        .outbrain-placeholder,
        .trc_related_container,
        #cookie-law-info-bar {
            display: none !important;
            visibility: hidden !important;
            height: 0 !important;
            min-height: 0 !important;
            opacity: 0 !important;
            pointer-events: none !important;
        }
    """
}
