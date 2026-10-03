package fr.berliat.hskwidget.domain

import io.github.vinceglb.filekit.BookmarkData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.fromBookmarkData

actual object BackupFolderAccess {
    actual fun fromBookmarkData(bookmark: BookmarkData): PlatformFile =
        PlatformFile.fromBookmarkData(bookmark)
}