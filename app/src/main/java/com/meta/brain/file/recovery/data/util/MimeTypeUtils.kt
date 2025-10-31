package com.meta.brain.file.recovery.data.util

import com.meta.brain.file.recovery.data.model.MediaKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Utility class for MIME type detection and MediaKind determination
 * Single responsibility: Handle all MIME type and media classification logic
 */
@Singleton
class MimeTypeUtils @Inject constructor() {

    /**
     * Get MIME type from file extension
     */
    fun getMimeTypeFromExtension(extension: String): String? {
        return when (extension.lowercase()) {
            // Image formats
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "bmp" -> "image/bmp"
            "heic" -> "image/heic"
            "heif" -> "image/heif"
            // Video formats
            "mp4" -> "video/mp4"
            "avi" -> "video/avi"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "3gp" -> "video/3gpp"
            "flv" -> "video/x-flv"
            "m4v" -> "video/x-m4v"
            "wmv" -> "video/x-ms-wmv"
            // Audio formats
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "aac" -> "audio/aac"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "flac" -> "audio/flac"
            "wma" -> "audio/x-ms-wma"
            "opus" -> "audio/opus"
            "amr" -> "audio/amr"
            "3ga" -> "audio/3gpp"
            // Document formats
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "apk" -> "application/vnd.android.package-archive"
            else -> null
        }
    }

    /**
     * Determine MediaKind from MIME type
     */
    fun determineMediaKind(mimeType: String?): MediaKind {
        return when {
            mimeType == null -> MediaKind.OTHER
            mimeType.startsWith("image/") -> MediaKind.IMAGE
            mimeType.startsWith("video/") -> MediaKind.VIDEO
            mimeType.startsWith("audio/") -> MediaKind.AUDIO
            mimeType.startsWith("application/") -> MediaKind.DOCUMENT
            mimeType.startsWith("text/") -> MediaKind.DOCUMENT
            else -> MediaKind.OTHER
        }
    }

    /**
     * Check if a file extension represents a media file (image, video, or audio)
     */
    fun isMediaExtension(extension: String): Boolean {
        val mimeType = getMimeTypeFromExtension(extension)
        return mimeType?.let {
            it.startsWith("image/") || it.startsWith("video/") || it.startsWith("audio/")
        } ?: false
    }

    /**
     * Check if a file extension represents a document
     */
    fun isDocumentExtension(extension: String): Boolean {
        val mimeType = getMimeTypeFromExtension(extension)
        return mimeType?.let {
            it.startsWith("application/") || it.startsWith("text/")
        } ?: false
    }

    /**
     * Get all supported document MIME types
     */
    fun getSupportedDocumentMimeTypes(): List<String> {
        return listOf(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "application/zip",
            "application/vnd.android.package-archive"
        )
    }
}

