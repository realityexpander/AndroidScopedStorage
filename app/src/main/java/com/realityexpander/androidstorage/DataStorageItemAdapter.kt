package com.realityexpander.androidstorage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import com.realityexpander.androidstorage.databinding.ItemExternalPhotoBinding
import com.realityexpander.androidstorage.databinding.ItemGroupTitleBinding
import com.realityexpander.androidstorage.databinding.ItemInternalPhotoBinding

import kotlinx.coroutines.*

typealias LayoutResourceId = Int

class DataStorageItemAdapter(
    private val onExternalStoragePhotoClick: (ExternalStoragePhoto) -> Unit = {},
    private val onInternalStoragePhotoClick: (InternalStoragePhoto) -> Unit = {},
    private val context: Context
) : BaseRecyclerAdapter<BaseDataStorageItem>() {

    enum class LayoutItemKind(val layoutId: Int) {
        GROUP_TITLE_ITEM (R.layout.item_group_title),
        EXTERNAL_STORAGE_PHOTO_ITEM (R.layout.item_external_photo),
        INTERNAL_STORAGE_PHOTO_ITEM (R.layout.item_internal_photo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: LayoutResourceId): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)

        return when (viewType) { // this is just a layout resource id
            LayoutItemKind.GROUP_TITLE_ITEM.layoutId ->
                GroupTitleViewHolder(inflater, parent, viewType)
            LayoutItemKind.EXTERNAL_STORAGE_PHOTO_ITEM.layoutId ->
                ExternalStoragePhotoViewHolder(inflater, parent, viewType, onExternalStoragePhotoClick)
            LayoutItemKind.INTERNAL_STORAGE_PHOTO_ITEM.layoutId ->
                InternalStoragePhotoViewHolder(inflater, parent, viewType, onInternalStoragePhotoClick)
            else ->
                throw IllegalArgumentException("Unknown viewType: $viewType")
        }
    }

    override fun getItemViewType(position: Int): LayoutResourceId {
        return getItem(position)?.let {
            return when (it) {
                is GroupTitle ->
                    LayoutItemKind.GROUP_TITLE_ITEM.layoutId
                is ExternalStoragePhoto ->
                    LayoutItemKind.EXTERNAL_STORAGE_PHOTO_ITEM.layoutId
                is InternalStoragePhoto ->
                    LayoutItemKind.INTERNAL_STORAGE_PHOTO_ITEM.layoutId
                else -> throw IllegalArgumentException("Unknown viewType: $it")
            }
        } ?: throw IllegalArgumentException("Unknown viewType for position: $position")
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val dataStorageItem = getItem(position) ?: return

        when (holder) {
            is GroupTitleViewHolder ->
                holder.bind(dataStorageItem as GroupTitle)
            is ExternalStoragePhotoViewHolder ->
                holder.bind(dataStorageItem as ExternalStoragePhoto)
            is InternalStoragePhotoViewHolder ->
                holder.bind(dataStorageItem as InternalStoragePhoto)
        }
    }

    class GroupTitleViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        layoutResourceId: Int
    ) : RecyclerView.ViewHolder(inflater.inflate(layoutResourceId, parent, false)) {
        private val binding = ItemGroupTitleBinding.bind(itemView)

        fun bind(groupTitleData: GroupTitle) {
            binding.tvGroupTitle.text = groupTitleData.title
        }
    }

    class ExternalStoragePhotoViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        layoutResourceId: Int,
        private val onSharedStoragePhotoClick: (ExternalStoragePhoto) -> Unit
    ) : RecyclerView.ViewHolder(inflater.inflate(layoutResourceId, parent, false)) {
//    ) : RecyclerView.ViewHolder(ItemPhotoBinding.bind(ItemPhotoBinding.inflate(inflater, parent, false)).root) {

        private val binding = ItemExternalPhotoBinding.bind(itemView)
        //private val binding = ItemPhotoBinding.inflate(inflater, parent, false)

        fun bind(photoData: ExternalStoragePhoto) {

            println("onBindViewHolder externalStoragePhotoViewHolder: ${photoData.id}, $position")

//            holder.binding.apply {
                if (photoData.height > 400 && photoData.width > 400) {
                    GlobalScope.launch {
                        val bitmap = resizeImageUri(photoData.contentUri, binding.root.context)
                        withContext(Dispatchers.Main) {
                            binding.ivPhoto.setImageBitmap(bitmap)
                        }
                    }
                } else {
                    binding.ivPhoto.setImageURI(photoData.contentUri)
                }

                val aspectRatio = photoData.width.toFloat() / photoData.height.toFloat()
                ConstraintSet().apply {
                    clone(binding.root)
                    setDimensionRatio(binding.ivPhoto.id, aspectRatio.toString())
                    applyTo(binding.root)
                }

                binding.ivPhoto.setOnLongClickListener {
                    onSharedStoragePhotoClick(photoData)
                    true
                }
//            }
        }
    }

    class InternalStoragePhotoViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        layoutResourceId: Int,
        private val onInternalStoragePhotoClick: (InternalStoragePhoto) -> Unit
    ) : RecyclerView.ViewHolder(inflater.inflate(layoutResourceId, parent, false)) {
        private val binding = ItemInternalPhotoBinding.bind(itemView)

        fun bind(photoData: InternalStoragePhoto) {
            println("onBindViewHolder InternalStoragePhotoViewHolder: ${photoData.name}, $position")

            val aspectRatio = photoData.bmp.width.toFloat() / photoData.bmp.height.toFloat()
            ConstraintSet().apply {
                clone(binding.root)
                setDimensionRatio(binding.ivPhoto.id, aspectRatio.toString())
                applyTo(binding.root)
            }

            binding.ivPhoto.setImageBitmap(photoData.bmp)

            binding.ivPhoto.setOnLongClickListener {
                onInternalStoragePhotoClick(photoData)
                true
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