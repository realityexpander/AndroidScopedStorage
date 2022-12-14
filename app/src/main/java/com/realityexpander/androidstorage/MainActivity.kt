package com.realityexpander.androidstorage

import android.Manifest
import android.app.RecoverableSecurityException
import android.content.ContentUris
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.realityexpander.androidstorage.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.*

// Good example of using ConcatAdapter: https://github.com/akexorcist/ConcatAdapterMultipleLayoutManager

// Public Storage is the same as External Storage
// Private Storage is the same as Internal Storage

// If you take a photo with the camera, and save in Public storage, this app can delete it without extra user dialog.
// If you take a photo with the camera, and save in Private storage, this app can delete it without extra user dialog.
// For any photo in the Public area (that was not added by this app), a delete will show a permission dialog to confirm deletion.

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private lateinit var internalStoragePhotoAdapter: DataStorageItemAdapter
    private lateinit var externalStoragePhotoAdapter: DataStorageItemAdapter

    private var readPermissionGranted = false
    private var writePermissionGranted = false
    private lateinit var permissionsLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    private lateinit var contentObserver: ContentObserver

    private var deletedImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        internalStoragePhotoAdapter = DataStorageItemAdapter(
            onInternalStoragePhotoClick = {
                lifecycleScope.launch {
                    val isDeletionSuccessful = deletePhotoFromInternalStorage(it.name)
                    if(isDeletionSuccessful) {
                        loadPhotosFromInternalStorageIntoRecyclerView()
                        Toast.makeText(this@MainActivity, "Photo successfully deleted", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to delete photo", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
        externalStoragePhotoAdapter = DataStorageItemAdapter(
            onExternalStoragePhotoClick = {
                lifecycleScope.launch {
                    deletePhotoFromExternalStorage(it.contentUri)
                    deletedImageUri = it.contentUri
                }
            }
        )

        permissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            readPermissionGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionGranted
            writePermissionGranted = permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionGranted

            if(readPermissionGranted) {
                loadPhotosFromExternalStorageIntoRecyclerView()
            } else {
                Toast.makeText(this, "Can't read files without permission.", Toast.LENGTH_LONG).show()
            }
        }
        updateOrRequestPermissions()

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if(it.resultCode == RESULT_OK) {
                if(Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    // For Q, we need to delete the image from the MediaStore after getting permission from the user.
                    lifecycleScope.launch {
                        deletePhotoFromExternalStorage(deletedImageUri ?: return@launch)
                    }
                }

                // For Build.VERSION_CODES.R, the OS will delete the file. No need to do anything here.

                Toast.makeText(this@MainActivity, "Photo deleted successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "Photo couldn't be deleted", Toast.LENGTH_SHORT).show()
            }
        }

        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            lifecycleScope.launch {
                val isPrivate = binding.switchPrivate.isChecked
                val isSavedSuccessfully =
                    when {
                        isPrivate ->
                            savePhotoToInternalStorage(UUID.randomUUID().toString(), it)

                        writePermissionGranted ->
                            savePhotoToExternalStorage(UUID.randomUUID().toString(), it)

                        else -> false
                    }

                if(isPrivate) {
                    loadPhotosFromInternalStorageIntoRecyclerView()
                }
                if(isSavedSuccessfully) {
                    Toast.makeText(this@MainActivity, "Photo saved successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to save photo", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }


        loadPhotosFromInternalStorageIntoRecyclerView()
        loadPhotosFromExternalStorageIntoRecyclerView()
        initContentObserver()
        setupConcatRecyclerView()
    }

    private suspend fun loadPhotosFromInternalStorage(): List<InternalStoragePhoto> {
        return withContext(Dispatchers.IO) {
            val files = filesDir.listFiles()
            files?.filter {
                    it.canRead() && it.isFile && it.name.endsWith(".jpg")
                }?.map{
                    val bytes = it.readBytes()
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                    InternalStoragePhoto(it.name, bmp)
                } ?: listOf()
        }
    }

    private suspend fun loadPhotosFromExternalStorage(): List<ExternalStoragePhoto> {
        return withContext(Dispatchers.IO) {

            val collection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.WIDTH,
                MediaStore.Images.Media.HEIGHT,
            )
            val photos = mutableListOf<ExternalStoragePhoto>()

            contentResolver.query(
                collection,
                projection,
                null,
                null,
                "${MediaStore.Images.Media.DISPLAY_NAME} ASC"
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val displayNameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                var index = 0

                while(cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val displayName = cursor.getString(displayNameColumn)
                    val width = cursor.getInt(widthColumn)
                    val height = cursor.getInt(heightColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        id
                    )

                    index++
                    //println("index: $index, displayName: $displayName, width: $width, height: $height")
                    photos.add(ExternalStoragePhoto(id, displayName, width, height, contentUri))
                }

                photos.toList()
            } ?: listOf()
        }
    }

    private suspend fun savePhotoToInternalStorage(filename: String, bmp: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                openFileOutput("$filename.jpg", MODE_PRIVATE).use { stream ->
                    if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, stream)) {
                        throw IOException("Couldn't save bitmap.")
                    }
                }
                true
            } catch(e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun savePhotoToExternalStorage(displayName: String, bmp: Bitmap): Boolean {
        return withContext(Dispatchers.IO) {
            val imageCollection = sdk29AndUp {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } ?: MediaStore.Images.Media.EXTERNAL_CONTENT_URI

            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$displayName.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.WIDTH, bmp.width)
                put(MediaStore.Images.Media.HEIGHT, bmp.height)
            }
            try {
                contentResolver.insert(imageCollection, contentValues)?.also { uri ->
                    contentResolver
                        .openOutputStream(uri)
                        .use { outputStream ->
                            if(!bmp.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                                throw IOException("Couldn't save bitmap")
                            }
                        }
                } ?: throw IOException("Couldn't create MediaStore entry")

                true
            } catch(e: IOException) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun deletePhotoFromInternalStorage(filename: String): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                deleteFile(filename)
            } catch (e: Exception) {
                e.printStackTrace()
                false
            }
        }
    }

    private suspend fun deletePhotoFromExternalStorage(photoUri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // Attempt to delete the photo (if the app created it, it will be deleted successfully)
                contentResolver.delete(photoUri, null, null)
            } catch (e: SecurityException) {

                // If could not delete the photo, then we need to request confirmation from the user.
                val intentSender = when {
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                        MediaStore.createDeleteRequest(contentResolver, listOf(photoUri)).intentSender
                    }
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                        val recoverableSecurityException = e as? RecoverableSecurityException
                        recoverableSecurityException?.userAction?.actionIntent?.intentSender
                    }
                    else -> null
                }

                // Have the user confirm the deletion of the photo.
                intentSender?.let { sender ->
                    intentSenderLauncher.launch(
                        IntentSenderRequest.Builder(sender).build()
                    )
                }
            }
        }
    }

    private fun loadPhotosFromExternalStorageIntoRecyclerView() {
        lifecycleScope.launch {
            val photos =
                listOf(
                    GroupTitle("Public/external storage"),
                ) + loadPhotosFromExternalStorage()

            externalStoragePhotoAdapter.setList(photos.toMutableList())
            externalStoragePhotoAdapter.notifyDataChanged()
        }
    }

    private fun loadPhotosFromInternalStorageIntoRecyclerView() {
        lifecycleScope.launch {
            val photos =
                listOf(
                    GroupTitle("Private/internal storage"),
                ) + loadPhotosFromInternalStorage()

            internalStoragePhotoAdapter.setList(photos.toMutableList())
            internalStoragePhotoAdapter.notifyDataChanged()
        }
    }

    private fun setupConcatRecyclerView() = binding.rvDataStorageItems.apply {
        adapter = ConcatAdapter(internalStoragePhotoAdapter, externalStoragePhotoAdapter)
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun initContentObserver() {
        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                if(readPermissionGranted) {
                    loadPhotosFromExternalStorageIntoRecyclerView()
                }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    private fun updateOrRequestPermissions() {
        val hasReadPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val hasWritePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionsToRequest = mutableListOf<String>()
        if(!writePermissionGranted) {
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if(!readPermissionGranted) {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if(permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }
}