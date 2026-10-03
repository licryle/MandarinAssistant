package fr.berliat.hskwidget.domain

import io.github.vinceglb.filekit.BookmarkData
import io.github.vinceglb.filekit.PlatformFile

/**
 * Platform folder bookmarks with durable access.
 *
 * Github issue: github.com/vinceglb/FileKit/issues/667
 *
 * FileKit's iOS bookmarks carry no security scope, so a stored folder
 * resolves but loses sandbox access on the next launch (the pick-session
 * grant dies with the process). This wrapper creates security-scoped
 * bookmarks on iOS — plain FileKit bookmarks everywhere else — and resolves
 * them with scoped access held.
 */
expect object BackupFolderAccess {

    /**
     * Resolve [bookmark] to a usable folder. Tries scoped resolution first,
     * falls back to plain FileKit resolution so legacy bookmarks keep working
     * while their session grant is alive. Throws when neither works.
     */
    fun fromBookmarkData(bookmark: BookmarkData): PlatformFile
}