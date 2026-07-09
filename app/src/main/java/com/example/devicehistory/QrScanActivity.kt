package com.example.devicehistory

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory

class QrScanActivity : AppCompatActivity() {
    private lateinit var barcodeView: DecoratedBarcodeView
    private var scannerStarted = false
    private var handledResult = false

    private val callback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult?) {
            if (handledResult) return
            val text = result?.text?.trim().orEmpty()
            if (text.isEmpty()) return
            handledResult = true
            setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SCAN_RESULT, text))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_qr_scan)

        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }
        barcodeView = findViewById(R.id.barcodeView)
        barcodeView.statusView.text = ""

        if (hasCameraPermission()) {
            startScanner()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        if (scannerStarted && hasCameraPermission()) {
            barcodeView.resume()
        }
    }

    override fun onPause() {
        if (scannerStarted) {
            barcodeView.pause()
        }
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMERA) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startScanner()
        } else {
            finish()
        }
    }

    private fun startScanner() {
        if (scannerStarted) return
        scannerStarted = true
        barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        barcodeView.decodeContinuous(callback)
        barcodeView.resume()
    }

    private fun hasCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val EXTRA_SCAN_RESULT = "scan_result"
        private const val REQUEST_CAMERA = 1001
    }
}
