package fr.berliat.hskwidget.ui

import fr.berliat.hskwidget.Res
import fr.berliat.hskwidget.data.model.WordList
import fr.berliat.hskwidget.data.model.WordListWithCount
import fr.berliat.hskwidget.data.type.SystemList
import fr.berliat.hskwidget.enum_system_list_annotated_words
import fr.berliat.hskwidget.enum_system_list_at_exam
import fr.berliat.hskwidget.enum_system_list_hsk_1
import fr.berliat.hskwidget.enum_system_list_hsk_2
import fr.berliat.hskwidget.enum_system_list_hsk_3
import fr.berliat.hskwidget.enum_system_list_hsk_4
import fr.berliat.hskwidget.enum_system_list_hsk_5
import fr.berliat.hskwidget.enum_system_list_hsk_6
import fr.berliat.hskwidget.enum_system_list_hsk_7
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

fun SystemList.toRes(): StringResource = when (this) {
    SystemList.HSK1 -> Res.string.enum_system_list_hsk_1
    SystemList.HSK2 -> Res.string.enum_system_list_hsk_2
    SystemList.HSK3 -> Res.string.enum_system_list_hsk_3
    SystemList.HSK4 -> Res.string.enum_system_list_hsk_4
    SystemList.HSK5 -> Res.string.enum_system_list_hsk_5
    SystemList.HSK6 -> Res.string.enum_system_list_hsk_6
    SystemList.HSK7 -> Res.string.enum_system_list_hsk_7
    SystemList.ANNOTATED_WORDS -> Res.string.enum_system_list_annotated_words
    SystemList.AT_EXAM -> Res.string.enum_system_list_at_exam
}

/** Maps a raw DB list name to its [SystemList], or null if not a system list. */
fun String.toSystemListOrNull(): SystemList? = SystemList.fromDbName(this)

/** Display name: localized resource for system lists, raw name for user lists. */
@Composable
fun WordList.localizedName(): String {
    val system = name.toSystemListOrNull()
    return if (system != null) stringResource(system.toRes()) else name
}

/** Display name: localized resource for system lists, raw name for user lists. */
@Composable
fun WordListWithCount.localizedName(): String {
    val system = name.toSystemListOrNull()
    return if (system != null) stringResource(system.toRes()) else name
}
