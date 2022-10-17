package com.realityexpander.androidstorage

import android.graphics.Bitmap
import android.net.Uri

open class BaseDataStorageItem {
    override fun equals(other: Any?): Boolean {
        return super.equals(other)
    }
    override fun hashCode(): Int {
        return javaClass.hashCode()
    }
}

data class GroupTitle(
    val title: String
) : BaseDataStorageItem()

data class ExternalStoragePhoto(
    val id: Long,
    val name: String,
    val width: Int,
    val height: Int,
    val contentUri: Uri
) : BaseDataStorageItem()

data class InternalStoragePhoto(
    val name: String,
    val bmp: Bitmap
) : BaseDataStorageItem()

