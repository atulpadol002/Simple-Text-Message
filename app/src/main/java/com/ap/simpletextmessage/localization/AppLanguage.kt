package com.ap.simpletextmessage.localization

enum class AppLanguage(
    val languageTag: String,
    val nativeName: String,
    val englishName: String? = null
) {
    SYSTEM_DEFAULT("", "System Default"),
    ENGLISH("en", "English"),
    HINDI("hi", "हिन्दी", "Hindi"),
    MARATHI("mr", "मराठी", "Marathi"),
    GUJARATI("gu", "ગુજરાતી", "Gujarati"),
    URDU("ur", "اردو", "Urdu"),
    SPANISH("es", "Español", "Spanish"),
    PORTUGUESE_BRAZIL("pt-BR", "Português (Brasil)", "Portuguese (Brazil)"),
    FRENCH("fr", "Français", "French"),
    GERMAN("de", "Deutsch", "German"),
    ITALIAN("it", "Italiano", "Italian"),
    INDONESIAN("id", "Bahasa Indonesia", "Indonesian"),
    TURKISH("tr", "Türkçe", "Turkish"),
    ARABIC("ar", "العربية", "Arabic"),
    RUSSIAN("ru", "Русский", "Russian"),
    JAPANESE("ja", "日本語", "Japanese"),
    KOREAN("ko", "한국어", "Korean"),
    SIMPLIFIED_CHINESE("zh-CN", "简体中文", "Simplified Chinese");

    companion object {
        fun fromLanguageTag(tag: String?): AppLanguage =
            entries.firstOrNull { it.languageTag.equals(tag.orEmpty(), ignoreCase = true) }
                ?: SYSTEM_DEFAULT
    }
}

enum class LanguageScreenOrigin(val routeValue: String) {
    DRAWER("drawer"),
    SECOND_SESSION("second_session");

    companion object {
        fun fromRouteValue(value: String?): LanguageScreenOrigin =
            entries.firstOrNull { it.routeValue == value } ?: DRAWER
    }
}

object LanguageFlowPolicy {
    fun localeTags(language: AppLanguage): String = language.languageTag

    fun shouldShowSecondSession(sessionNumber: Int, alreadyShown: Boolean): Boolean =
        sessionNumber == 2 && !alreadyShown

    fun shouldShowDoneInterstitial(origin: LanguageScreenOrigin): Boolean =
        origin == LanguageScreenOrigin.DRAWER
}
