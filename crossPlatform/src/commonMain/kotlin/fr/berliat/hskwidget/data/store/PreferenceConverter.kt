package fr.berliat.hskwidget.data.store

import io.github.vinceglb.filekit.BookmarkData
import fr.berliat.hskwidget.domain.BackupFolderAccess

import kotlin.io.encoding.Base64

open class PreferenceConverter<S, T>(
    val fromStore: (S) -> T,
    val toStore: (T) -> S
)

class FileKitBookmarkPreferenceConverter : PreferenceConverter<String, BookmarkData?>(
    fromStore = { stored ->
        try {
            if (stored == "") {
                null
            } else {
                // Let's try to decode, if it fails, goes into the catch. We don't need to keep invalid data
                val bookMark = BookmarkData(Base64.decode(stored))
                BackupFolderAccess.fromBookmarkData(bookMark)

                bookMark
            }
        } catch (_: Exception) {
            null
        }
    },
    toStore = { value -> if (value == null) {
            ""
        } else {
            Base64.encode(value.bytes)
        }
    }
)