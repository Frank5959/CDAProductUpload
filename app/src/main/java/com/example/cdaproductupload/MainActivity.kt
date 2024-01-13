package com.example.cdaproductupload

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.cdaproductupload.databinding.ActivityMainBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val binding by lazy { ActivityMainBinding.inflate(layoutInflater) }
    private var selectedImages = mutableListOf<Uri>()
    private lateinit var selectImageLauncher: ActivityResultLauncher<Intent>
    private val productsStorage = FirebaseStorage.getInstance().reference
    private val db = FirebaseFirestore.getInstance()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)


        binding.buttonImagesPicker.setOnClickListener {
            pickImages()
        }

        binding.btnsave.setOnClickListener {
            val productValidation = validationInformation()
            if (productValidation) {
                saveProduct()
                Toast.makeText(this, "Uploaded successfully..", Toast.LENGTH_SHORT).show()
            }
           else Toast.makeText(this, "Please check your inputs..", Toast.LENGTH_SHORT).show()
        }
        selectImageLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val intent = result.data
                    if (intent?.clipData != null) {
                        val count = intent.clipData?.itemCount ?: 0
                        (0 until count).forEach {
                            val imageUri = intent.clipData?.getItemAt(it)?.uri
                            imageUri?.let {
                                selectedImages.add(imageUri)
                            }
                        }
                    } else {
                        val imageUri = intent?.data
                        imageUri?.let { selectedImages.add(imageUri) }
                    }
                    updateImagesCount()
                }
            }
    }

    private fun pickImages() {
        val intent = Intent(Intent.ACTION_GET_CONTENT)
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        intent.type = "image/*"
        selectImageLauncher.launch(intent)
    }

    private fun updateImagesCount() {
        binding.tvSelectedImages.text = selectedImages.size.toString()
    }

    /*override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menutoolbar,menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.saveProduct) {
            val productValidation = validationInformation()
            if (productValidation) {
                saveProduct()
            }
            Toast.makeText(this, "Please check your inputs..", Toast.LENGTH_SHORT).show()
            return false
        }
        return super.onOptionsItemSelected(item)
    }*/

    private fun saveProduct() {
        val name = binding.edName.text.toString().trim()
        val category = binding.edCategory.text.toString().trim()
        val price = binding.edPrice.text.toString().trim()
        val offerPercentage = binding.offerPercentage.text.toString().trim()
        val description = binding.edDescription.text.toString().trim()
        val sizes = sizesList(binding.edSizes.text.toString().trim())
        val imagesByteArrays = getImagesByteArrays()
        val images = mutableListOf<String>()

        lifecycleScope.launch(Dispatchers.Main) {
            showLoading()
            try {
                async {
                    imagesByteArrays.forEach {
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imageStore = productsStorage.child("products/images/$id")
                            val result = imageStore.putBytes(it).await()
                            val downloadUrl = result.storage.downloadUrl.await().toString()
                            images.add(downloadUrl)
                        }
                    }
                }.await()
            } catch (e: Exception) {
                e.printStackTrace()
                hideLoading()
            }
            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),
                if (offerPercentage.isEmpty()) null else offerPercentage.toFloat(),
                description.ifEmpty { null },
                sizes,
                images
            )
            db.collection("products").add(product).addOnSuccessListener {
                hideLoading()
            }.addOnFailureListener { e ->
                hideLoading()
                Log.e("Error", e.message, e)
            }
        }
    }

    private fun hideLoading() {
        binding.loadingProgressbar.visibility = View.INVISIBLE
    }

    private fun showLoading() {
        binding.loadingProgressbar.visibility = View.VISIBLE
    }

    private fun getImagesByteArrays(): List<ByteArray> {
        val imagesByteArray = mutableListOf<ByteArray>()
        selectedImages.forEach {
            val stream = ByteArrayOutputStream()
            val imageBmp = MediaStore.Images.Media.getBitmap(contentResolver, it)
            if (imageBmp.compress(Bitmap.CompressFormat.JPEG, 100, stream)) {
                imagesByteArray.add(stream.toByteArray())
            }
        }
        return imagesByteArray
    }

    private fun sizesList(sizesString: String): List<String>? {
        if (sizesString.isEmpty())
            return null
        return sizesString.split(",")
    }

    private fun validationInformation(): Boolean {
        if (binding.edName.text.toString().trim().isEmpty())
            return false
        if (binding.edPrice.text.toString().trim().isEmpty())
            return false
        if (binding.edCategory.text.toString().trim().isEmpty())
            return false
        return selectedImages.isNotEmpty()
    }
}
