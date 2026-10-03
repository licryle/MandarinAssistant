package fr.berliat.hskwidget.domain

import io.github.vinceglb.filekit.BookmarkData
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.fromBookmarkData
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.BooleanVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.UnsafeNumber
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLBookmarkResolutionWithSecurityScope
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, UnsafeNumber::class)
actual object BackupFolderAccess {
    actual fun fromBookmarkData(bookmark: BookmarkData): PlatformFile {
        return scopedResolve(bookmark) ?: PlatformFile.fromBookmarkData(bookmark)
    }

    private fun scopedResolve(bookmark: BookmarkData): PlatformFile? = memScoped {
        val errorPtr = alloc<ObjCObjectVar<NSError?>>()
        val stalePtr = alloc<BooleanVar>()
        val url = NSURL.URLByResolvingBookmarkData(
            bookmarkData = bookmark.bytes.toNSData(),
            options = NSURLBookmarkResolutionWithSecurityScope,
            relativeToURL = null,
            bookmarkDataIsStale = stalePtr.ptr,
            error = errorPtr.ptr
        ) ?: return null
        if (!url.startAccessingSecurityScopedResource()) return null
        // Held for process lifetime (released at termination): a single start
        // per launch, no per-op stop balancing needed.
        PlatformFile(url)
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class, UnsafeNumber::class)
private fun ByteArray.toNSData(): NSData = usePinned {
    NSData.create(
        bytes = it.addressOf(0),
        length = size.toULong()
    )
}