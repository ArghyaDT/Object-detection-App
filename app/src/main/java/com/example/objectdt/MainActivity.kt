package com.example.objectdt

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import android.view.Window
import android.widget.ImageView
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.ml.AutoModel1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class MainActivity : AppCompatActivity() {

    var colors = listOf<Int>(Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
        Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    lateinit var textureView: TextureView
    lateinit var cameraManager:CameraManager
    lateinit var handler:Handler
    lateinit var cameraDivice:CameraDevice
    lateinit var imageView:ImageView
    lateinit var bitmap: Bitmap
    lateinit var model:AutoModel1
    lateinit var imageProcessor: ImageProcessor
    val paint= Paint()
    lateinit var labels:List<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.activity_main)

        setContentView(R.layout.activity_main)
        get_permission()


        labels = FileUtil.loadLabels(this,"labels.txt")
        imageProcessor=ImageProcessor.Builder().add(ResizeOp(300,300,ResizeOp.ResizeMethod.BILINEAR)).build()
        model = AutoModel1.newInstance(this)
        val handlerThread=HandlerThread("videoThread")
        handlerThread.start()
        handler=Handler(handlerThread.looper)

        imageView=findViewById(R.id.imageView)

        textureView = findViewById(R.id.textureView)
        textureView.surfaceTextureListener=object:TextureView.SurfaceTextureListener{
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int) {
                open_camera()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int) {

            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture) {
                bitmap=textureView.bitmap!!

                var image = TensorImage.fromBitmap(bitmap)
                image = imageProcessor.process(image)

                val outputs = model.process(image)
                val locations = outputs.locationsAsTensorBuffer.floatArray
                val classes = outputs.classesAsTensorBuffer.floatArray
                val scores = outputs.scoresAsTensorBuffer.floatArray
                val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                var mutable=bitmap.copy(Bitmap.Config.ARGB_8888,true)
                val canvas=Canvas(mutable)

                var h = mutable.height
                var w = mutable.width


                paint.textSize = h/15f
                paint.strokeWidth = h/85f
                scores.forEachIndexed { index, fl ->
                    if(fl > 0.5){
                        var x = index
                        x *= 4
                        paint.setColor(colors.get(index))
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(locations.get(x+1)*w, locations.get(x)*h, locations.get(x+3)*w, locations.get(x+2)*h), paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(labels[classes.get(index).toInt()] + " " + fl.toString(), locations.get(x+1)*w, locations.get(x)*h, paint)
                    }
                }

                imageView.setImageBitmap(mutable)

            }
        }

        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onDestroy() {
        super.onDestroy()
        model.close()
    }

    @SuppressLint("MissingPermission")
    fun open_camera(){
        cameraManager.openCamera(cameraManager.cameraIdList[0], object:CameraDevice.StateCallback(){

            override fun onOpened(p0: CameraDevice) {
                cameraDivice=p0

                var surfaceTexture=textureView.surfaceTexture
                var surface= Surface(surfaceTexture)

                var captureRequest=cameraDivice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequest.addTarget(surface)

                cameraDivice.createCaptureSession(listOf(surface), object:CameraCaptureSession.StateCallback(){
                    override fun onConfigured(p0: CameraCaptureSession) {
                        p0.setRepeatingRequest(captureRequest.build(), null,null)
                    }

                    override fun onConfigureFailed(p0: CameraCaptureSession) {

                    }
                },handler)
            }

            override fun onDisconnected(p0: CameraDevice) {

            }

            override fun onError(p0: CameraDevice, p1: Int) {

            }
        },handler)
    }

    fun get_permission() {
        if(ContextCompat.checkSelfPermission(this,android.Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.CAMERA), 101)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if(grantResults[0] !=PackageManager.PERMISSION_GRANTED){
            get_permission()
        }
    }
}