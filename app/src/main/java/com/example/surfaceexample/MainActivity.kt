package com.example.surfaceexample

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.surfaceexample.databinding.ActivityMainBinding
import com.google.android.gms.tasks.Task
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.ktx.Firebase
import com.google.firebase.ktx.app
import com.google.firebase.ktx.options
import com.google.gson.*
import java.io.*
import java.lang.Exception
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.R

import android.graphics.BitmapFactory




class MainActivity : AppCompatActivity() {

    private lateinit var mBinding : ActivityMainBinding

    private var imageCapture : ImageCapture? = null
    private lateinit var outputDirectory : File
    private lateinit var cameraExecutor : ExecutorService

    private var mBitMap : Bitmap? = null

    private lateinit var functions: FirebaseFunctions



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mBinding.root)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Set up the listener for take photo button
        mBinding.cameraCaptureButton.setOnClickListener { takePhoto() }
        outputDirectory = getOutputDirectory()
        cameraExecutor = Executors.newSingleThreadExecutor()

        functions = FirebaseFunctions.getInstance()

    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.KOREA
            ).format(System.currentTimeMillis()) + ".png")

        // Create output options object which contains file + metadata
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        imageCapture.takePicture(
            outputOptions
            , ContextCompat.getMainExecutor(this)
            , object : ImageCapture.OnImageSavedCallback {

                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {

                    val savedUri = Uri.fromFile(photoFile)
                    saveImage(savedUri)

                    try {
                        mBitMap = BitmapFactory.decodeResource(
                            resources,
                            R.drawable.alert_dark_frame
                        )
                        //mBitMap = MediaStore.Images.Media.getBitmap(contentResolver , savedUri)
                        if (mBitMap != null) {
                            mBitMap = Utils.scaleBitmapDown(mBitMap!!, 640)

                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

                                val request = JsonObject()
                                val image = JsonObject()
                                image.add("content", JsonPrimitive(bitMapToBase64(mBitMap!!)))
                                request.add("image", image)
                                //Add features to the request
                                val feature = JsonObject()
                                feature.add("type", JsonPrimitive("TEXT_DETECTION"))
                                // Alternatively, for DOCUMENT_TEXT_DETECTION:
                                // feature.add("type", JsonPrimitive("DOCUMENT_TEXT_DETECTION"))
                                val features = JsonArray()
                                features.add(feature)
                                request.add("features", features)

                                val imageContext = JsonObject()
                                val languageHints = JsonArray()
                                languageHints.add("kr")
                                imageContext.add("languageHints", languageHints)
                                request.add("imageContext", imageContext)


                                annotateImage(request.toString())
                                    .addOnCompleteListener { task ->
                                        if (!task.isSuccessful) {
                                            // Task failed with an exception
                                            // ...
                                            Log.e(TAG , "failed!!!!->${
                                                task.exception?.message} / ${task.exception?.localizedMessage} ")
                                        } else {
                                            // Task completed successfully
                                            // ...
                                            val annotation = task.result!!.asJsonArray[0]
                                                .asJsonObject["fullTextAnnotation"].asJsonObject

                                            System.out.format("%nComplete annotation:")
                                            System.out.format("%n%s", annotation["text"].asString)
                                        }
                                    }
                            }
                        }
                    } catch (iEcp: IOException) {
                        Log.d(TAG, "bitMap Error ~ ${iEcp.message}")
                    }

                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }

    private fun annotateImage(requestJson: String): Task<JsonElement> {
        return functions
            .getHttpsCallable("annotateImage")
            .call(requestJson)
            .continueWith { task ->
                // This continuation runs on either success or failure, but if the task
                // has failed then result will throw an Exception which will be
                // propagated down.
                val result = task.result?.data
                JsonParser.parseString(Gson().toJson(result))
            }
    }


    @RequiresApi(Build.VERSION_CODES.O)
    private fun bitMapToBase64(bitmap : Bitmap) : String{
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val imageBytes: ByteArray = byteArrayOutputStream.toByteArray()
        return Base64.getEncoder().encodeToString(imageBytes)
    }

    private fun saveImage( imageUri : Uri ) {

        val values = ContentValues()
        val fileName = "${System.currentTimeMillis()}_temp.png"
        values.put(MediaStore.Images.Media.DISPLAY_NAME , fileName)
        values.put(MediaStore.Images.Media.MIME_TYPE , "image/*")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val contentResolver = contentResolver
        val item = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI , values)

        try {

            val pdf = contentResolver.openFileDescriptor(item!! , "w" , null)

            if (pdf == null) {
                Log.d("syc","null")
            } else {

                val inputData = Utils.getBytes(imageUri , contentResolver)
                val fos = FileOutputStream(pdf.fileDescriptor)
                fos.write(inputData)
                fos.close()

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    values.clear();
                    values.put(MediaStore.Images.Media.IS_PENDING, 0);
                    contentResolver.update(item, values, null, null);
                }

            }

        } catch (ext : Exception) {
            Log.d("file","fileUploadFail -> ${ext.message}")
        }

        val file = File(fileName)
        MediaScannerConnection.scanFile(applicationContext,
            arrayOf(file.toString()),
            null, null)
    }


    private fun startCamera() {

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener( {
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider : ProcessCameraProvider = cameraProviderFuture.get()

            //preview
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(mBinding.viewFinder.surfaceProvider)
                }


            imageCapture = ImageCapture.Builder()
                .build()


//            val imageAnalyzer = ImageAnalysis.Builder()
//                .build()
//                .also {
//                    it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
//                        Log.d(TAG, "Average luminosity: $luma")
//                    })
//                }

            //select back camera as a default
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                //unbind use cases before rebinding
                cameraProvider.unbindAll()

                //Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this as LifecycleOwner
                    , cameraSelector
                    , preview
                    ,imageCapture
                )

            } catch (exc : Exception) {
                Log.e("MainActivity1","Use case binding failed" , exc)
            }
        },ContextCompat.getMainExecutor(this))

    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext, it) == PackageManager.PERMISSION_GRANTED
    }


    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, "").apply { mkdirs() } }

        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }


    override fun onRequestPermissionsResult(
        requestCode: Int
        , permissions: Array<String>
        , grantResults: IntArray) {

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT).show()
                finish()
                super.onRequestPermissionsResult(requestCode, permissions, grantResults)
            }
        }
    }





    companion object {
        private const val TAG = "CameraXBasic"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA
            , Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }





}