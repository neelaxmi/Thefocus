package com.neelaxmi.focus.core

/** Our own coarse categories (the Android SDK category is only a hint, and many apps leave it undefined). */
enum class Category { SOCIAL, ENTERTAINMENT, GAMES, BROWSERS, COMMUNICATION, PRODUCTIVITY, OTHER }

/** SDK hint mapped by the platform layer from ApplicationInfo.category (kept as an enum so the engine has no Android dependency). */
enum class PlatformCategory { GAME, VIDEO, AUDIO, SOCIAL, PRODUCTIVITY, NEWS, MAPS, IMAGE, UNDEFINED }

data class AppRef(val pkg: String, val label: String, val category: Category)

enum class GroupKind { SOCIAL, ENTERTAINMENT, GAMES, BROWSERS, CUSTOM }

/** A named set of apps. [enabled] = applies during focus sessions. Schedules pick groups explicitly. */
data class BlockGroup(val id: Long, val name: String, val kind: GroupKind, val enabled: Boolean, val apps: Set<String>)

object AppCategorizer {
    // Well-known public package names. Facts about the Play Store ecosystem, not copied data.
    private val social = setOf("com.instagram.android", "com.instagram.lite", "com.facebook.katana", "com.facebook.lite", "com.snapchat.android",
        "com.twitter.android", "com.zhiliaoapp.musically", "com.reddit.frontpage", "com.pinterest", "com.linkedin.android", "in.mohalla.sharechat", "com.tumblr")
    private val entertainment = setOf("com.google.android.youtube", "com.netflix.mediaclient", "com.spotify.music", "in.startv.hotstar",
        "com.amazon.avod.thirdpartyclient", "com.mxtech.videoplayer.ad", "tv.twitch.android.app", "com.jio.media.ondemand", "in.mohalla.video", "com.google.android.apps.youtube.music")
    private val games = setOf("com.supercell.clashofclans", "com.supercell.brawlstars", "com.tencent.ig", "com.dts.freefireth", "com.activision.callofduty.shooter",
        "com.kiloo.subwaysurf", "com.king.candycrushsaga", "com.roblox.client", "com.mojang.minecraftpe", "com.innersloth.spacemafia", "com.miHoYo.GenshinImpact")
    private val browsers = setOf("com.android.chrome", "org.mozilla.firefox", "com.brave.browser", "com.opera.browser", "com.opera.mini.native", "com.microsoft.emmx",
        "com.duckduckgo.mobile.android", "com.sec.android.app.sbrowser", "com.UCMobile.intl", "com.android.browser", "com.kiwibrowser.browser", "org.mozilla.fenix")
    private val communication = setOf("com.whatsapp", "org.telegram.messenger", "com.google.android.apps.messaging", "com.google.android.gm", "com.discord", "org.thoughtcrime.securesms")

    /** Known-package lists win over the SDK hint; the hint is used only for unknown packages. */
    fun categorize(pkg: String, hint: PlatformCategory = PlatformCategory.UNDEFINED): Category = when {
        pkg in social -> Category.SOCIAL
        pkg in entertainment -> Category.ENTERTAINMENT
        pkg in games -> Category.GAMES
        pkg in browsers -> Category.BROWSERS
        pkg in communication -> Category.COMMUNICATION
        else -> when (hint) {
            PlatformCategory.GAME -> Category.GAMES
            PlatformCategory.VIDEO, PlatformCategory.AUDIO -> Category.ENTERTAINMENT
            PlatformCategory.SOCIAL -> Category.SOCIAL
            PlatformCategory.PRODUCTIVITY -> Category.PRODUCTIVITY
            else -> Category.OTHER
        }
    }

    /** Categories treated as "distracting" in analytics (in addition to anything the user put in an enabled block group). */
    val distractingCategories = setOf(Category.SOCIAL, Category.ENTERTAINMENT, Category.GAMES)

    fun groupKindFor(c: Category): GroupKind? = when (c) {
        Category.SOCIAL -> GroupKind.SOCIAL; Category.ENTERTAINMENT -> GroupKind.ENTERTAINMENT
        Category.GAMES -> GroupKind.GAMES; Category.BROWSERS -> GroupKind.BROWSERS; else -> null
    }
}

object DefaultGroups {
    val names = mapOf(GroupKind.SOCIAL to "Social Media", GroupKind.ENTERTAINMENT to "Entertainment", GroupKind.GAMES to "Games",
        GroupKind.BROWSERS to "Browsers", GroupKind.CUSTOM to "Custom Apps")

    /** Browsers are NOT enabled by default (they are needed for studying); everything else is. */
    fun enabledByDefault(kind: GroupKind) = kind != GroupKind.BROWSERS

    /** Suggests members for the built-in groups from what is really installed. Custom group starts empty. */
    fun suggest(installed: List<AppRef>): Map<GroupKind, Set<String>> {
        val m = GroupKind.entries.associateWith { mutableSetOf<String>() }
        installed.forEach { a -> AppCategorizer.groupKindFor(a.category)?.let { m.getValue(it) += a.pkg } }
        return m
    }

    /** Package set treated as distracting for analytics. */
    fun distractingPackages(installed: List<AppRef>, groups: List<BlockGroup>): Set<String> =
        installed.filter { it.category in AppCategorizer.distractingCategories }.map { it.pkg }.toSet() +
            groups.filter { it.enabled }.flatMap { it.apps }
}
