package com.agy.uml.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object AssetInstaller {

    /**
     * Checks if core UML engine binaries exist in app-private storage.
     * If not, extracts them from APK assets and sets executable permissions.
     */
    suspend fun ensureBinariesInstalled(context: Context): Boolean = withContext(Dispatchers.IO) {
        val filesDir = context.filesDir
        val requiredBinaries = listOf("linux", "stub_exe", "umnet", "passt")
        
        var allPresent = true
        for (bin in requiredBinaries) {
            val file = File(filesDir, bin)
            if (!file.exists()) {
                allPresent = false
                break
            }
        }

        if (allPresent) {
            return@withContext true
        }

        // Extract binaries from assets if present
        val assetManager = context.assets
        for (bin in requiredBinaries) {
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
                } catch (e: Exception) {
                    // Binary not in bundled assets; user can provide it or download it
                }
            } else {
                targetFile.setExecutable(true, false)
            }
        }

        true
    }
}
