package com.realityexpander.androidstorage

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.realityexpander.androidstorage.databinding.ItemExternalPhotoBinding
import com.realityexpander.androidstorage.databinding.ItemGroupTitleBinding
import com.realityexpander.androidstorage.databinding.ItemInternalPhotoBinding
import kotlinx.coroutines.*

const val MAX_IMAGE_WIDTH_OR_HEIGHT = 400
typealias LayoutResourceId = Int

class DataStorageItemAdapter(
    private val onExternalStoragePhotoClick: (ExternalStoragePhoto) -> Unit = {},
    private val onInternalStoragePhotoClick: (InternalStoragePhoto) -> Unit = {},
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
                ExternalStoragePhotoViewHolder(inflater, parent,viewType, onExternalStoragePhotoClick)

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

                else ->
                    throw IllegalArgumentException("Unknown viewType: $it")
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

            else ->
                throw IllegalArgumentException("Unknown holder: $holder")
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

            // Use the whole row for this item
            (this.binding.root.layoutParams as StaggeredGridLayoutManager.LayoutParams).isFullSpan = true
        }
    }

    class ExternalStoragePhotoViewHolder(
        inflater: LayoutInflater,
        parent: ViewGroup,
        layoutResourceId: Int,
        private val onSharedStoragePhotoClick: (ExternalStoragePhoto) -> Unit
    ) : RecyclerView.ViewHolder(inflater.inflate(layoutResourceId, parent, false)) {

        private val binding = ItemExternalPhotoBinding.bind(itemView)
        //private val binding = ItemPhotoBinding.inflate(inflater, parent, false)

        fun bind(photoData: ExternalStoragePhoto) {
            binding.apply {

                if (photoData.height > MAX_IMAGE_WIDTH_OR_HEIGHT || photoData.width > MAX_IMAGE_WIDTH_OR_HEIGHT) {
                    GlobalScope.launch {
                        val bitmap = resizeImageUri(photoData.contentUri, binding.root.context)
                        withContext(Dispatchers.Main) {
                            ivPhoto.setImageBitmap(bitmap)
                        }
                    }
                } else {
                    ivPhoto.setImageURI(photoData.contentUri)
                }

                val aspectRatio = photoData.width.toFloat() / photoData.height.toFloat()
                ConstraintSet().apply {
                    clone(root)
                    setDimensionRatio(ivPhoto.id, aspectRatio.toString())
                    applyTo(root)
                }

                ivPhoto.setOnLongClickListener {
                    onSharedStoragePhotoClick(photoData)
                    true
                }
            }
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
            binding.apply {
                val aspectRatio = photoData.bmp.width.toFloat() / photoData.bmp.height.toFloat()
                ConstraintSet().apply {
                    clone(root)
                    setDimensionRatio(ivPhoto.id, aspectRatio.toString())
                    applyTo(root)
                }

                ivPhoto.setImageBitmap(photoData.bmp)

                ivPhoto.setOnLongClickListener {
                    onInternalStoragePhotoClick(photoData)
                    true
                }
            }
        }
    }
}

// Resize ImageUri to max MAX_IMAGE_WIDTH_OR_HEIGHT
private suspend fun resizeImageUri(uri: Uri, context: Context): Bitmap? {
    val scope = CoroutineScope(Dispatchers.Default)

    return scope.async {
        val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r", null) ?: return@async null
        val fileDescriptor = parcelFileDescriptor.fileDescriptor
        val image = BitmapFactory.decodeFileDescriptor(fileDescriptor)
        parcelFileDescriptor.close()

        val max = MAX_IMAGE_WIDTH_OR_HEIGHT
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
}