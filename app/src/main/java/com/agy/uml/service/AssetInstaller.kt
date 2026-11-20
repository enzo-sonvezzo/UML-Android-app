package com.agy.uml.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

object AssetInstaller {

    private const val TAG = "AssetInstaller"

    /**
     * Checks if the required UML engine binaries exist either in
     * applicationInfo.nativeLibraryDir (recommended for Android 10+ SELinux W^X)
     * or app filesDir as fallback.
     */
    suspend fun ensureBinariesInstalled(context: Context): Boolean = withContext(Dispatchers.IO) {
        val libDir = File(context.applicationInfo.nativeLibraryDir)
        val filesDir = context.filesDir

        val nativeLibs = listOf("liblinux.so", "libstub_exe.so")
        val presentInNativeLibDir = nativeLibs.all { File(libDir, it).exists() }

        if (presentInNativeLibDir) {
            Log.i(TAG, "Native UML binaries verified in nativeLibraryDir: ${libDir.absolutePath}")
            return@withContext true
        }

        Log.w(TAG, "Native libs not found in nativeLibraryDir, checking filesDir fallback...")

        // Fallback: check filesDir
        val fallbackLibs = listOf("linux", "stub_exe")
        val presentInFilesDir = fallbackLibs.all { File(filesDir, it).exists() }
        if (presentInFilesDir) {
            Log.i(TAG, "UML binaries found in filesDir: ${filesDir.absolutePath}")
            return@withContext true
        }

        // Try extracting from assets/bin if packaged
        val assetManager = context.assets
        var extractedAny = false
        for (bin in listOf("linux", "stub_exe", "umnet", "passt")) {
            val targetFile = File(filesDir, bin)
            if (!targetFile.exists()) {
                try {
                    assetManager.open("bin/$bin").use { input ->
                        FileOutputStream(targetFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                    targetFile.setExecutable(true, false)
                    targetFile.setReadable(true, false)
                    extractedAny = true
                    Log.i(TAG, "Extracted asset: $bin to ${targetFile.absolutePath}")
                } catch (e: Exception) {
                    Log.d(TAG, "Asset bin/$bin not in APK assets: ${e.message}")
                }
            }
        }

        val finalCheck = File(libDir, "liblinux.so").exists() || File(filesDir, "linux").exists()
        if (!finalCheck) {
            Log.e(TAG, "CRITICAL: UML kernel binary (liblinux.so / linux) is missing! Kernel cannot boot.")
        }
        finalCheck
    }

    /**
     * Dynamically expands the virtual capacity of an ext4 sparse disk.
     * Takes 0 ms and uses 0 bytes of physical storage until guest writes data.
     * The guest Linux kernel will automatically online-resize the filesystem via resize2fs.
     */
    fun expandSparseDisk(targetFile: File, targetSizeGb: Long): Boolean {
        return try {
            val currentBytes = targetFile.length()
            val targetBytes = targetSizeGb * 1024L * 1024L * 1024L
            if (targetBytes > currentBytes) {
                RandomAccessFile(targetFile, "rw").use { raf ->
                    raf.setLength(targetBytes)
                }
                Log.i(TAG, "Expanded sparse disk ${targetFile.name} to ${targetSizeGb} GB virtual capacity")
                true
            } else {
                Log.w(TAG, "Disk is already ${currentBytes / (1024 * 1024 * 1024)} GB, cannot shrink")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to expand sparse disk: ${e.message}", e)
            false
        }
    }

    /**
     * Creates an empty sparse disk of the specified virtual capacity (default 32 GB).
     * Physical storage consumption on Android F2FS/ext4 is initially ~0 bytes.
     */
    fun createEmptySparseDisk(targetFile: File, sizeGb: Long = 32L): Boolean {
        return try {
            if (targetFile.exists()) targetFile.delete()
            RandomAccessFile(targetFile, "rw").use { raf ->
                raf.setLength(sizeGb * 1024L * 1024L * 1024L)
            }
            Log.i(TAG, "Created sparse disk ${targetFile.absolutePath} of $sizeGb GB")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create sparse disk: ${e.message}", e)
            false
        }
    }
}
