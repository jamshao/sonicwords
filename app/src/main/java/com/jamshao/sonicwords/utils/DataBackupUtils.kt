package com.jamshao.sonicwords.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.room.Room
import com.jamshao.sonicwords.data.database.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object DataBackupUtils {
    private const val TAG = "DataBackupUtils"
    private const val PREFERENCES_NAME = "sonicwords_settings"
    
    suspend fun backupData(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 获取数据库文件
            val dbFile = context.getDatabasePath("sonicwords.db")
            if (!dbFile.exists()) {
                Log.e(TAG, "数据库文件不存在")
                return@withContext false
            }
            
            // 获取SharedPreferences文件
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            val sharedPrefsFile = File(sharedPrefsDir, "$PREFERENCES_NAME.xml")
            if (!sharedPrefsFile.exists()) {
                Log.e(TAG, "设置文件不存在")
                // 继续备份，不影响数据库备份
            }
            
            // 创建临时文件夹
            val tempDir = File(context.cacheDir, "backup_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
            
            // 复制数据库文件
            val tempDbFile = File(tempDir, "sonicwords.db")
            dbFile.copyTo(tempDbFile, overwrite = true)
            
            // 复制设置文件（如果存在）
            val tempPrefsFile = File(tempDir, "$PREFERENCES_NAME.xml")
            if (sharedPrefsFile.exists()) {
                sharedPrefsFile.copyTo(tempPrefsFile, overwrite = true)
            }
            
            // 创建ZIP归档
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                ZipOutputStream(outputStream).use { zipOut ->
                    // 添加数据库文件
                    addFileToZip(zipOut, tempDbFile, "sonicwords.db")
                    
                    // 添加设置文件（如果存在）
                    if (tempPrefsFile.exists()) {
                        addFileToZip(zipOut, tempPrefsFile, "$PREFERENCES_NAME.xml")
                    }
                }
            }
            
            // 清理临时文件
            tempDir.deleteRecursively()
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "备份数据失败", e)
            return@withContext false
        }
    }
    
    suspend fun restoreData(context: Context, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            // 创建临时文件夹
            val tempDir = File(context.cacheDir, "restore_temp")
            if (tempDir.exists()) {
                tempDir.deleteRecursively()
            }
            tempDir.mkdirs()
            
            // 解压ZIP文件
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zipIn ->
                    var entry: ZipEntry? = zipIn.nextEntry
                    while (entry != null) {
                        val newFile = File(tempDir, entry.name)
                        newFile.parentFile?.mkdirs()
                        
                        FileOutputStream(newFile).use { fos ->
                            val buffer = ByteArray(1024)
                            var len: Int
                            while (zipIn.read(buffer).also { len = it } > 0) {
                                fos.write(buffer, 0, len)
                            }
                        }
                        
                        zipIn.closeEntry()
                        entry = zipIn.nextEntry
                    }
                }
            }
            
            // 检查数据库文件
            val tempDbFile = File(tempDir, "sonicwords.db")
            if (!tempDbFile.exists()) {
                Log.e(TAG, "备份中没有数据库文件")
                return@withContext false
            }
            
            // 关闭数据库连接（如果有）
            val db = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "sonicwords.db"
            ).build()
            db.close()
            
            // 还原数据库文件
            val dbFile = context.getDatabasePath("sonicwords.db")
            dbFile.parentFile?.mkdirs()
            tempDbFile.copyTo(dbFile, overwrite = true)
            
            // 还原设置文件（如果存在）
            val tempPrefsFile = File(tempDir, "$PREFERENCES_NAME.xml")
            if (tempPrefsFile.exists()) {
                val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
                val sharedPrefsFile = File(sharedPrefsDir, "$PREFERENCES_NAME.xml")
                sharedPrefsDir.mkdirs()
                tempPrefsFile.copyTo(sharedPrefsFile, overwrite = true)
            }
            
            // 清理临时文件
            tempDir.deleteRecursively()
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "恢复数据失败", e)
            return@withContext false
        }
    }
    
    suspend fun clearData(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            // 关闭数据库连接
            val db = Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "sonicwords.db"
            ).build()
            db.close()
            
            // 删除数据库文件
            val dbFile = context.getDatabasePath("sonicwords.db")
            if (dbFile.exists()) {
                dbFile.delete()
            }
            
            // 删除设置文件
            val sharedPrefsDir = File(context.applicationInfo.dataDir, "shared_prefs")
            val sharedPrefsFile = File(sharedPrefsDir, "$PREFERENCES_NAME.xml")
            if (sharedPrefsFile.exists()) {
                sharedPrefsFile.delete()
            }
            
            return@withContext true
        } catch (e: Exception) {
            Log.e(TAG, "清除数据失败", e)
            return@withContext false
        }
    }
    
    private fun addFileToZip(zipOut: ZipOutputStream, file: File, entryName: String) {
        if (!file.exists()) {
            return
        }
        
        val fis = FileInputStream(file)
        val zipEntry = ZipEntry(entryName)
        zipOut.putNextEntry(zipEntry)
        
        val bytes = ByteArray(1024)
        var length: Int
        while (fis.read(bytes).also { length = it } >= 0) {
            zipOut.write(bytes, 0, length)
        }
        
        fis.close()
        zipOut.closeEntry()
    }
} 