package com.entoknakal.plugins.file_picker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import com.nativephp.mobile.bridge.BridgeError
import com.nativephp.mobile.bridge.BridgeFunction
import com.nativephp.mobile.bridge.BridgeResponse
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object FilePickerFunctions {

    class OpenPicker(private val activity: Activity) : BridgeFunction {

        override fun execute(parameters: Map<String, Any>): Map<String, Any> {
            // 1. Ekstraksi parameter "types" dan "multiple" (default multiple = false)
            val rawTypes = extractRawTypes(parameters["types"])
            val isMultiple = extractMultipleParam(parameters["multiple"])
            val types = parseAndNormalizeTypes(rawTypes)
            val rawTypesFormatted = rawTypes.joinToString(", ")

            val componentActivity = activity as? ComponentActivity
                ?: throw BridgeError.ExecutionFailed("Activity is not a ComponentActivity")

            val latch = CountDownLatch(1)
            val selectedUris = mutableListOf<Uri>()
            var errorException: Exception? = null

            val finalAllowedTypes = types.toList()

            componentActivity.runOnUiThread {
                try {
                    val registryKey = "file_picker_" + System.currentTimeMillis()
                    var launcher: ActivityResultLauncher<Array<String>>? = null

                    // Custom Contract untuk menangani Single dan Multiple File Selection
                    val customContract = object : ActivityResultContract<Array<String>, List<Uri>>() {
                        override fun createIntent(context: Context, input: Array<String>): Intent {
                            return Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                                addCategory(Intent.CATEGORY_OPENABLE)
                                if (isMultiple) {
                                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                }
                                val validTypes = input.filter { it.isNotBlank() }
                                if (validTypes.isNotEmpty() && !validTypes.contains("*/*")) {
                                    type = if (validTypes.size == 1) validTypes.first() else "*/*"
                                    putExtra(Intent.EXTRA_MIME_TYPES, validTypes.toTypedArray())
                                } else {
                                    type = "*/*"
                                }
                            }
                        }

                        override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> {
                            if (intent == null || resultCode != Activity.RESULT_OK) return emptyList()

                            val uris = mutableListOf<Uri>()
                            val clipData = intent.clipData

                            if (clipData != null) {
                                for (i in 0 until clipData.itemCount) {
                                    clipData.getItemAt(i)?.uri?.let { uris.add(it) }
                                }
                            } else {
                                intent.data?.let { uris.add(it) }
                            }
                            return uris
                        }
                    }

                    launcher = componentActivity.activityResultRegistry.register(
                        registryKey,
                        customContract
                    ) { uris: List<Uri> ->
                        try {
                            if (uris.isNotEmpty()) {
                                selectedUris.addAll(uris)
                            }
                        } catch (e: Exception) {
                            errorException = e
                        } finally {
                            launcher?.unregister()
                            latch.countDown()
                        }
                    }

                    launcher.launch(finalAllowedTypes.toTypedArray())
                } catch (e: Exception) {
                    errorException = e
                    latch.countDown()
                }
            }

            latch.await(5, TimeUnit.MINUTES)

            if (errorException != null) {
                val message = errorException?.message ?: "Gagal membuka file manager"
                return BridgeResponse.error(BridgeError.ExecutionFailed(message))
            }

            // Pengguna membatalkan pilihan
            if (selectedUris.isEmpty()) {
                return if (isMultiple) {
                    BridgeResponse.success(mapOf("files" to emptyList<Map<String, String>>()))
                } else {
                    BridgeResponse.success(emptyMap<String, String>())
                }
            }

            // Salin & Validasikan file di Background Thread
            return try {
                if (isMultiple) {
                    val resultsList = selectedUris.map { uri ->
                        processAndCopyUri(activity, uri, finalAllowedTypes, rawTypesFormatted)
                    }
                    // Dibungkus ke dalam map "files" agar sesuai dengan BridgeResponse.success(Map<String, Any>)
                    BridgeResponse.success(mapOf("files" to resultsList))
                } else {
                    val singleResult = processAndCopyUri(activity, selectedUris.first(), finalAllowedTypes, rawTypesFormatted)
                    BridgeResponse.success(singleResult)
                }
            } catch (e: Exception) {
                val message = e.message ?: "Gagal memproses file"
                BridgeResponse.error(BridgeError.ExecutionFailed(message))
            }
        }

        private fun extractMultipleParam(param: Any?): Boolean {
            return when (param) {
                is Boolean -> param
                is String -> param.trim().lowercase() == "true" || param.trim() == "1"
                is Number -> param.toInt() == 1
                else -> false
            }
        }

        private fun extractRawTypes(param: Any?): List<String> {
            if (param == null) return listOf("*/*")
            val result = mutableListOf<String>()

            when (param) {
                is List<*> -> {
                    for (item in param) {
                        item?.toString()?.trim()?.let { if (it.isNotBlank()) result.add(it) }
                    }
                }
                is Array<*> -> {
                    for (item in param) {
                        item?.toString()?.trim()?.let { if (it.isNotBlank()) result.add(it) }
                    }
                }
                is org.json.JSONArray -> {
                    for (i in 0 until param.length()) {
                        param.optString(i)?.trim()?.let { if (it.isNotBlank()) result.add(it) }
                    }
                }
                is String -> {
                    val str = param.trim()
                    if (str.startsWith("[") && str.endsWith("]")) {
                        val cleaned = str.substring(1, str.length - 1)
                        for (part in cleaned.split(",")) {
                            val item = part.trim().removeSurrounding("\"").removeSurrounding("'")
                            if (item.isNotBlank()) result.add(item)
                        }
                    } else if (str.isNotBlank()) {
                        result.add(str)
                    }
                }
            }

            return if (result.isEmpty()) listOf("*/*") else result
        }

        private fun parseAndNormalizeTypes(rawTypes: List<String>): List<String> {
            val resultMimes = mutableSetOf<String>()
            for (raw in rawTypes) {
                val cleaned = raw.trim().lowercase()
                if (cleaned == "*" || cleaned == "*/*" || cleaned == "all") {
                    return listOf("*/*")
                }
                when (cleaned) {
                    "images", "image", "photo", "photos", "picture", "pictures" -> resultMimes.add("image/*")
                    "videos", "video", "movie", "movies" -> resultMimes.add("video/*")
                    "audios", "audio", "sound", "sounds", "music" -> resultMimes.add("audio/*")
                    "pdf" -> resultMimes.add("application/pdf")
                    "zip" -> {
                        resultMimes.add("application/zip")
                        resultMimes.add("application/x-zip-compressed")
                    }
                    "rar" -> {
                        resultMimes.add("application/vnd.rar")
                        resultMimes.add("application/x-rar-compressed")
                    }
                    "word", "doc", "docx" -> {
                        resultMimes.add("application/msword")
                        resultMimes.add("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                    }
                    "excel", "xls", "xlsx" -> {
                        resultMimes.add("application/vnd.ms-excel")
                        resultMimes.add("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                    }
                    "ppt", "pptx", "powerpoint" -> {
                        resultMimes.add("application/vnd.ms-powerpoint")
                        resultMimes.add("application/vnd.openxmlformats-officedocument.presentationml.presentation")
                    }
                    "txt", "text" -> resultMimes.add("text/plain")
                    "csv" -> resultMimes.add("text/csv")
                    else -> {
                        if (cleaned.contains("/")) {
                            resultMimes.add(cleaned)
                        } else {
                            val ext = cleaned.removePrefix(".")
                            val inferred = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                            if (inferred != null) {
                                resultMimes.add(inferred)
                            } else {
                                resultMimes.add("application/$ext")
                            }
                        }
                    }
                }
            }
            return if (resultMimes.isEmpty()) listOf("*/*") else resultMimes.toList()
        }

        private fun processAndCopyUri(
            context: Context, 
            uri: Uri, 
            allowedTypes: List<String>, 
            rawTypesFormatted: String
        ): Map<String, String> {
            val contentResolver = context.contentResolver
            var fileName = "file_${System.currentTimeMillis()}"

            try {
                contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) {
                            val name = cursor.getString(nameIndex)
                            if (!name.isNullOrEmpty()) {
                                fileName = name
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            val fileLogExt = fileName.substringAfterLast('.', "").lowercase()
            var mimeType = contentResolver.getType(uri)
            
            val inferredMime = if (fileLogExt.isNotEmpty()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileLogExt)
            } else null

            if (!inferredMime.isNullOrEmpty()) {
                mimeType = inferredMime
            } else if (mimeType.isNullOrEmpty()) {
                mimeType = "application/octet-stream"
            }
            val finalMimeType = mimeType ?: "application/octet-stream"

            if (allowedTypes.isNotEmpty() && !allowedTypes.contains("*/*")) {
                val isAllowed = allowedTypes.any { allowed ->
                    when {
                        allowed == "*/*" -> true
                        allowed.endsWith("/*") -> {
                            val prefix = allowed.substringBefore("/*")
                            finalMimeType.startsWith("$prefix/")
                        }
                        else -> {
                            if (allowed.equals(finalMimeType, ignoreCase = true)) {
                                true
                            } else {
                                val allowedExt = MimeTypeMap.getSingleton().getExtensionFromMimeType(allowed)
                                if (!allowedExt.isNullOrEmpty() && allowedExt.equals(fileLogExt, ignoreCase = true)) {
                                    true
                                } else {
                                    val cleanAllowed = allowed.substringAfterLast('/').removePrefix(".")
                                    cleanAllowed.equals(fileLogExt, ignoreCase = true)
                                }
                            }
                        }
                    }
                }

                if (!isAllowed) {
                    throw Exception("File '$fileName' bertipe ($finalMimeType). Harap pilih file dengan format: [$rawTypesFormatted].")
                }
            }

            val cacheFolder = File(context.cacheDir, "picked_files").apply { mkdirs() }
            val localFile = File(cacheFolder, "${System.currentTimeMillis()}_$fileName")

            contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("Tidak dapat membaca data dari file '$fileName'.")

            return mapOf(
                "name" to fileName,
                "path" to localFile.absolutePath,
                "mime_type" to finalMimeType,
                "size" to localFile.length().toString()
            )
        }
    }
}