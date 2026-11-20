package com.agy.uml.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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
}
