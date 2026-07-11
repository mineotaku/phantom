package com.example.security

import android.content.Context
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.File

object ExifStripper {
    private const val TAG = "ExifStripper"

    private val TAGS_TO_STRIP = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_DATETIME,
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_SOFTWARE,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_ISO_SPEED_RATINGS,
        ExifInterface.TAG_ARTIST,
        ExifInterface.TAG_COPYRIGHT,
        ExifInterface.TAG_USER_COMMENT
    )

    fun stripExifData(imageBytes: ByteArray): ByteArray {
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("exif_strip_", ".jpg")
            tempFile.writeBytes(imageBytes)

            val exifInterface = ExifInterface(tempFile.absolutePath)
            var modified = false

            for (tag in TAGS_TO_STRIP) {
                if (exifInterface.getAttribute(tag) != null) {
                    exifInterface.setAttribute(tag, null)
                    modified = true
                }
            }

            if (modified) {
                exifInterface.saveAttributes()
                return tempFile.readBytes()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to strip EXIF data", e)
        } finally {
            tempFile?.delete()
        }
        return imageBytes
    }

    fun stripExifFromUri(context: Context, uri: Uri): ByteArray? {
        return try {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
            stripExifData(bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading from URI for EXIF stripping", e)
            null
        }
    }

    fun hasExifData(imageBytes: ByteArray): Boolean {
        var tempFile: File? = null
        try {
            tempFile = File.createTempFile("exif_check_", ".jpg")
            tempFile.writeBytes(imageBytes)

            val exifInterface = ExifInterface(tempFile.absolutePath)
            for (tag in TAGS_TO_STRIP) {
                if (exifInterface.getAttribute(tag) != null) {
                    return true
                }
            }
        } catch (e: Exception) {
            // ignore
        } finally {
            tempFile?.delete()
        }
        return false
    }
}
