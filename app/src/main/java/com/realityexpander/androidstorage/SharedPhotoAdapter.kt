package com.realityexpander.androidstorage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.AsyncDifferConfig
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.realityexpander.androidstorage.databinding.ItemExternalPhotoBinding
import com.realityexpander.androidstorage.databinding.ItemGroupTitleBinding
import kotlinx.coroutines.*



class SharedPhotoAdapter(
    private val onPhotoClick: (ExternalStoragePhoto) -> Unit
) : ListAdapter<BaseDataStorageItem, RecyclerView.ViewHolder>(AsyncDifferConfig.Builder(Companion).build()) {

    enum class ItemKind(val layoutId: Int) {
        PHOTO (R.layout.item_external_photo),
        GROUP_TITLE (R.layout.item_group_title)
    }

    inner class PhotoViewHolder(val binding: ItemExternalPhotoBinding): RecyclerView.ViewHolder(binding.root)
    inner class GroupViewHolder(val binding: ItemGroupTitleBinding): RecyclerView.ViewHolder(binding.root)

    companion object : DiffUtil.ItemCallback<BaseDataStorageItem>() {
        override fun areItemsTheSame(oldItem: BaseDataStorageItem, newItem: BaseDataStorageItem): Boolean {
            return when(oldItem) {
                is ExternalStoragePhoto ->  oldItem.id == (newItem as ExternalStoragePhoto).id
                else -> oldItem == newItem
            }
        }

        override fun areContentsTheSame(oldItem: BaseDataStorageItem, newItem: BaseDataStorageItem): Boolean {
            return when(oldItem) {
                is ExternalStoragePhoto ->  oldItem == (newItem as ExternalStoragePhoto)
                else -> oldItem == newItem
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) { // this is just a layout resource id
            ItemKind.PHOTO.layoutId ->
                PhotoViewHolder(
                        ItemExternalPhotoBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            ItemKind.GROUP_TITLE.layoutId ->
                GroupViewHolder(
                        ItemGroupTitleBinding.inflate(
                        LayoutInflater.from(parent.context),
                        parent,
                        false
                    )
                )
            else ->
                throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if(position < 3)
            ItemKind.GROUP_TITLE.layoutId
        else
            ItemKind.PHOTO.layoutId
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = currentList[position]

        when(holder) {
            is PhotoViewHolder -> {
                val photo = item as ExternalStoragePhoto
                val holder = (holder as PhotoViewHolder)
                println("onBindViewHolder: ${photo.id}, $position")

                holder.binding.apply {
                    if (photo.height > 400 && photo.width > 400) {
                        GlobalScope.launch {
                            val bitmap = resizeImageUri(photo.contentUri, root.context)
                            withContext(Dispatchers.Main) {
                                ivPhoto.setImageBitmap(bitmap)
                            }
                        }
                    } else {
                        ivPhoto.setImageURI(photo.contentUri)
                    }

                    val aspectRatio = photo.width.toFloat() / photo.height.toFloat()
                    ConstraintSet().apply {
                        clone(root)
                        setDimensionRatio(ivPhoto.id, aspectRatio.toString())
                        applyTo(root)
                    }

                    ivPhoto.setOnLongClickListener {
                        onPhotoClick(photo)
                        true
                    }
                }
            }
            is GroupViewHolder -> {
                if (position in 1..2) {
                    val holder = holder as GroupViewHolder
                    holder.binding.tvGroupTitle.text = ""
                }
            }
        }
    }

    // Resize ImageUri to max 400x400
    private suspend fun resizeImageUri(uri: Uri, context: Context): Bitmap? {
        val scope = CoroutineScope(Dispatchers.Default)

        return scope.async {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r", null) ?: return@async null
            val fileDescriptor = parcelFileDescriptor.fileDescriptor
            val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
            parcelFileDescriptor.close()

            val max = 400
            val width = image.width
            val height = image.height
            val scale = if (width > height) {
                max.toFloat() / width
            } else {
                max.toFloat() / height
            }
            val matrix = android.graphics.Matrix()
            matrix.postScale(scale, scale)

            Bitmap.createBitmap(image, 0, 0, width, height, matrix, true)
        }.await()

        //return Bitmap.createScaledBitmap(image, 200, 200, true)  // does not respect aspect ratio
    }
}