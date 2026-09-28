package fr.berliat.hskwidget.core

import fr.berliat.hskwidget.*
import fr.berliat.hskwidget.data.type.SystemList
import org.jetbrains.compose.resources.getString

object CachedResources {
    // Fallback values for static access before init
    var appName: String = "Mandarin Assistant"
        private set
    var appSlogan: String = "Study at every phone unlock"
        private set
    var widgetListName: String = "Words from List"
        private set
    var widgetListDescription: String = "Read a random word from the lists you have configured."
        private set
    var widgetOCRName: String = "Take Picture/OCR Shortcut"
        private set
    var widgetOCRDescription: String = "Quickly take a picture to extract Mandarin text."
        private set
    var widgetNextWord: String = "Next Word"
        private set
    var widgetSpeakWord: String = "Speak Word"
        private set
    var widgetNotConfigured: String = "No word. Configure list(s)"
        private set
    var placeholderWord: String = "你好"
        private set
    var placeholderPinyin: String = "nǐ hǎo"
        private set
    var placeholderDefinition: String = "Hello"
        private set
    var placeholderLevel: String = "HSK 1"
        private set
    var placeholderLanguage: String = "en"
        private set
    var widgetConfigTitle: String = "Configure Widget"
        private set
    var widgetConfigDescription: String = "Vocabulary list to include:"
        private set
    var widgetConfigSelectedLists: String = "Selected Lists"
        private set
    /** DB list name -> localized display name for system lists. Keep in sync with SystemList.toRes(). */
    var systemListNames: Map<String, String> = emptyMap()
        private set

    suspend fun load(): CachedResources = CachedResources.apply {
        appName = getString(Res.string.app_name)
        appSlogan = getString(Res.string.app_slogan)
        widgetListName = getString(Res.string.widget_name)
        widgetListDescription = getString(Res.string.widget_description)
        widgetOCRName = getString(Res.string.ocr_shortcut_name)
        widgetOCRDescription = getString(Res.string.ocr_shortcut_description)
        widgetNextWord = getString(Res.string.dictionary_item_reload)
        widgetSpeakWord = getString(Res.string.widget_btn_speak)
        widgetNotConfigured = getString(Res.string.widget_not_configured)
        placeholderWord = getString(Res.string.widget_placeholder_word)
        placeholderPinyin = getString(Res.string.widget_placeholder_pinyin)
        placeholderDefinition = getString(Res.string.widget_placeholder_definition)
        placeholderLevel = getString(Res.string.widget_placeholder_level)
        placeholderLanguage = getString(Res.string.widget_placeholder_language)
        widgetConfigTitle = getString(Res.string.widget_configure)
        widgetConfigDescription = getString(Res.string.widget_configure_wordlist_title)
        widgetConfigSelectedLists = getString(Res.string.widget_config_selected_lists)
        systemListNames = mapOf(
            SystemList.HSK1.dbName to getString(Res.string.enum_system_list_hsk_1),
            SystemList.HSK2.dbName to getString(Res.string.enum_system_list_hsk_2),
            SystemList.HSK3.dbName to getString(Res.string.enum_system_list_hsk_3),
            SystemList.HSK4.dbName to getString(Res.string.enum_system_list_hsk_4),
            SystemList.HSK5.dbName to getString(Res.string.enum_system_list_hsk_5),
            SystemList.HSK6.dbName to getString(Res.string.enum_system_list_hsk_6),
            SystemList.HSK7.dbName to getString(Res.string.enum_system_list_hsk_7),
            SystemList.ANNOTATED_WORDS.dbName to getString(Res.string.enum_system_list_annotated_words),
            SystemList.AT_EXAM.dbName to getString(Res.string.enum_system_list_at_exam)
        )
    }
}
