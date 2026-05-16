package com.example.xtrtv.utils

import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

class UpdateManager(private val context: Context) {
    private val TAG = "UpdateManager"

    fun downloadAndInstall(url: String, fileName: String) {
        try {
            Log.d(TAG, "Starting download from $url")
            
            // Delete old file if it exists to avoid DownloadManager naming conflicts (e.g. filename(1).apk)
            val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val existingFile = File(storageDir, fileName)
            if (existingFile.exists()) {
                Log.d(TAG, "Deleting existing file: ${existingFile.absolutePath}")
                existingFile.delete()
            }
            
            // Also clean up any other .apk files in the download directory to be tidy
            storageDir?.listFiles { file -> file.extension == "apk" }?.forEach { 
                if (it.name != fileName) {
                    Log.d(TAG, "Cleaning up old apk: ${it.name}")
                    it.delete()
                }
            }

            Toast.makeText(context, "Laddar ner uppdatering...", Toast.LENGTH_LONG).show()
            
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val uri = Uri.parse(url)
            
            val request = DownloadManager.Request(uri).apply {
                setTitle("Uppdaterar XTR Tv")
                setDescription("Laddar ner ny version...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            }

            val downloadId = downloadManager.enqueue(request)
            Log.d(TAG, "Download enqueued with ID $downloadId")
            
            val onComplete = object : BroadcastReceiver() {
                @SuppressLint("Range")
                override fun onReceive(context: Context, intent: Intent) {
                    val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                    if (id == downloadId) {
                        Log.d(TAG, "Download completed, starting installation")
                        
                        val query = DownloadManager.Query().setFilterById(id)
                        val cursor = downloadManager.query(query)
                        if (cursor.moveToFirst()) {
                            val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))
                            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                                val localUri = cursor.getString(cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI))
                                if (localUri != null) {
                                    installApk(Uri.parse(localUri))
                                }
                            } else {
                                Log.e(TAG, "Download failed with status: $status")
                                Toast.makeText(context, "Nedladdning misslyckades", Toast.LENGTH_SHORT).show()
                            }
                        }
                        cursor.close()
                        
                        try {
                            context.unregisterReceiver(this)
                        } catch (e: Exception) {
                            // Already unregistered
                        }
                    }
                }
            }
            
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(onComplete, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            Toast.makeText(context, "Nedladdning misslyckades: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun installApk(fileUri: Uri) {
        try {
            Log.d(TAG, "Installing APK from URI: $fileUri")
            
            // DownloadManager returns a URI like file:///... or content://...
            // We need to make sure we can handle it. 
            // If it's a file URI, we convert it to a content URI via FileProvider for the installer
            
            val finalUri = if (fileUri.scheme == "file") {
                val file = File(fileUri.path!!)
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            } else {
                fileUri
            }

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(finalUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Installation failed", e)
            Toast.makeText(context, "Installation misslyckades: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
