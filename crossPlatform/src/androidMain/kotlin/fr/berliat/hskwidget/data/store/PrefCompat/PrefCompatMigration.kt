package fr.berliat.hskwidget.data.store.PrefCompat

import android.content.Context
import android.provider.DocumentsContract
import android.util.Log

import androidx.compose.ui.unit.sp

import fr.berliat.hskwidget.core.AppDispatchers
import fr.berliat.hskwidget.core.HSKAppServices
import fr.berliat.hskwidget.ui.widget.FlashcardWidgetProvider

import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.bookmarkData

import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import kotlinx.datetime.Instant

import fr.berliat.hskwidget.data.store.PrefCompat.AppPreferencesStore as OldAppPreferencesStore

object PrefCompatMigration {
    fun shouldMigrate(context : Context, newVersion: Int): Boolean {
        val oldAppPrefs = OldAppPreferencesStore(context)
        val previousVersion = oldAppPrefs.appVersionCode
        if (previousVersion == 0) return false // first launch, nothing to update

        val updateDbVersions = listOf(44)

        return updateDbVersions.any { updateVersion ->
            previousVersion < updateVersion && newVersion >= updateVersion
        }
    }

    fun migrate(context: Context) {
        HSKAppServices.appScope.launch(AppDispatchers.IO) {
            appPrefCompatMigration(context)
            widgetsPrefCompatMigration(context)
        }
    }

    private suspend fun widgetsPrefCompatMigration(context: Context)
            = withContext(AppDispatchers.IO) {
        FlashcardWidgetProvider.getWidgetIds().forEach {
            val oldPrefs = FlashcardPreferencesStore(context, it)
            val newPrefs = HSKAppServices.widgetsPreferencesProvider.invoke(it)

            newPrefs.currentWord.value = oldPrefs.currentSimplified.toString()

            oldPrefs.clear()
        }
    }

    private suspend fun appPrefCompatMigration(context: Context)
            = withContext(AppDispatchers.IO) {
        val oldAppPrefs = OldAppPreferencesStore(context)
        val newAppPrefs = HSKAppServices.appPreferences

        //newAppPrefs.searchQuery - Didn't have in the past
        //newAppPrefs.ankiModelId - Didn't have in the past
        //newAppPrefs.appVersionCode.value - No need to migrate
        newAppPrefs.supportTotalSpent.value = oldAppPrefs.supportTotalSpent
        newAppPrefs.ankiSaveNotes.value = oldAppPrefs.ankiSaveNotes

        newAppPrefs.dbBackupCloudLastSuccess.value = Instant.fromEpochMilliseconds(
            oldAppPrefs.dbBackupCloudLastSuccess.toEpochMilli()
        )

        try {
            /**
             * Convert my treeURI to documentURI because of this FileKit bug:
             * https://github.com/vinceglb/FileKit/issues/418
             */
            val treeUri = oldAppPrefs.dbBackUpDirectory
            val platformDirectory = treeUri.let {
                // Transform the treeUri to a documentUri
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri,
                    DocumentsContract.getTreeDocumentId(treeUri)
                )
                PlatformFile(documentUri)
            }

            newAppPrefs.dbBackUpDiskDirectory.value = platformDirectory.bookmarkData()
        } catch (_ : Exception) {
            Log.e(TAG, "Couldn't migrate folder permission to FileKit ${oldAppPrefs.dbBackUpDirectory}")
        }
        newAppPrefs.dbBackUpDiskActive.value = oldAppPrefs.dbBackUpActive
        newAppPrefs.dbBackUpDiskMaxFiles.value = oldAppPrefs.dbBackUpMaxLocalFiles

        newAppPrefs.readerShowAllPinyins.value = oldAppPrefs.readerShowAllPinyins
        newAppPrefs.readerTextSize.value = oldAppPrefs.readerTextSize.sp
        newAppPrefs.readerSeparateWords.value = oldAppPrefs.readerSeparateWords

        newAppPrefs.lastAnnotatedClassLevel.value = oldAppPrefs.lastAnnotatedClassLevel
        newAppPrefs.lastAnnotatedClassType.value = oldAppPrefs.lastAnnotatedClassType

        newAppPrefs.searchFilterHasAnnotation.value = oldAppPrefs.searchFilterHasAnnotation

        oldAppPrefs.clear()
    }

    private const val TAG = "PrefCompatMigration"
}