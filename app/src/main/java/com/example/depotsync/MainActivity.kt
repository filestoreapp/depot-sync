package com.example.depotsync

import android.Manifest
import android.app.AlertDialog
import android.content.ContentUris
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // --- CHANGE THESE ---
    private val backendUrl = "https://p01--tgdrive--n2gwp6mbt6tm.code.run/"
    private val accessToken = "62e97b5ebbab6aac5550fe840ccf15f40378d5a80d1e3b91"
    // ---------------------

    private val client = OkHttpClient()
    private val prefs by lazy { getSharedPreferences("depot_sync", MODE_PRIVATE) }

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                scanAndPrompt()
            } else {
                Toast.makeText(this, "Permissions required to sync media", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        checkPermissionsAndStart()
    }

    private fun checkPermissionsAndStart() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val notGranted = permissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (notGranted) {
            requestPermissionLauncher.launch(permissions.toTypedArray())
        } else {
            scanAndPrompt()
        }
    }

    private fun scanAndPrompt() {
        val lastSyncTime = prefs.getLong("last_sync_time", 0L)
        val newFiles = findNewMediaFiles(lastSyncTime)

        if (newFiles.isEmpty()) {
            Toast.makeText(this, "No new photos or videos to upload", Toast.LENGTH_SHORT).show()
            return
        }

        val totalSizeMB = newFiles.sumOf { it.size } / (1024 * 1024)
        AlertDialog.Builder(this)
            .setTitle("New media found")
            .setMessage("Found ${newFiles.size} new photos/videos (${totalSizeMB} MB). Upload to Depot?")
            .setPositiveButton("Upload") { _, _ ->
                uploadFiles(newFiles)
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun findNewMediaFiles(lastSyncTimeMillis: Long): List<MediaItem> {
        val files = mutableListOf<MediaItem>()
        val lastSyncSeconds = lastSyncTimeMillis / 1000

        files.addAll(queryMedia(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, lastSyncSeconds, true))
        files.addAll(queryMedia(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, lastSyncSeconds, false))

        return files.sortedBy { it.dateAdded }
    }

    private fun queryMedia(uri: Uri, lastSyncSeconds: Long, isImage: Boolean): List<MediaItem> {
        val items = mutableListOf<MediaItem>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATA
        )
        val selection = "${MediaStore.MediaColumns.DATE_ADDED} > ?"
        val selectionArgs = arrayOf(lastSyncSeconds.toString())
        val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} ASC"

        contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol)
                val size = cursor.getLong(sizeCol)
                val dateAdded = cursor.getLong(dateCol) * 1000
                val dataPath = cursor.getString(dataCol)

                val contentUri = ContentUris.withAppendedId(uri, id)
                items.add(MediaItem(contentUri, name, size, dateAdded, isImage))
            }
        }
        return items
    }

    private fun uploadFiles(files: List<MediaItem>) {
        CoroutineScope(Dispatchers.IO).launch {
            var successCount = 0
            var failCount = 0

            files.forEachIndexed { index, item ->
                try {
                    uploadSingleFile(item)
                    successCount++
                } catch (e: Exception) {
                    failCount++
                    e.printStackTrace()
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@MainActivity,
                        "Uploaded ${index + 1}/${files.size}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            prefs.edit().putLong("last_sync_time", System.currentTimeMillis()).apply()

            withContext(Dispatchers.Main) {
                Toast.makeText(
                    this@MainActivity,
                    "Sync complete. $successCount uploaded, $failCount failed.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun uploadSingleFile(item: MediaItem) {
        val inputStream = contentResolver.openInputStream(item.uri) ?: throw IOException("Cannot open file")

        val tempFile = File.createTempFile("upload", item.name, cacheDir)
        FileOutputStream(tempFile).use { output ->
            inputStream.copyTo(output)
        }

        val requestBody = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", item.name, tempFile.asRequestBody("application/octet-stream".toMediaTypeOrNull()))
            .addFormDataPart("folder", "/")
            .build()

        val request = Request.Builder()
            .url("$backendUrl/api/upload")
            .header("Authorization", "Bearer $accessToken")
            .post(requestBody)
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Upload failed: ${response.code} ${response.message}")
            }
        }

        tempFile.delete()
    }

    data class MediaItem(
        val uri: Uri,
        val name: String,
        val size: Long,
        val dateAdded: Long,
        val isImage: Boolean
    )
}
