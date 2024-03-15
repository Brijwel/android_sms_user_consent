package com.brijwel.smsuserconsent

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.brijwel.smsuserconsent.databinding.ActivityMainBinding
import com.google.android.gms.auth.api.phone.SmsRetriever
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Status
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        binding.start.setOnClickListener {
            SmsRetriever.getClient(this).startSmsUserConsent(null /*phone number or null */)
                .addOnSuccessListener {
                    timer.cancel()
                    timer.start()
                }.addOnFailureListener {
                    timer.cancel()
                    setTimerText(0L)
                }
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            smsVerificationReceiver,
            IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION),
            SmsRetriever.SEND_PERMISSION,
            null,
            ContextCompat.RECEIVER_EXPORTED
        )
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(smsVerificationReceiver)
    }

    private fun setTimerText(duration: Long) {
        lifecycleScope.launch(Dispatchers.Main) {
            binding.timer.text = String.format(
                "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(duration) % 60,
                TimeUnit.MILLISECONDS.toSeconds(duration) % 60
            )
        }
    }

    private val smsVerificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (SmsRetriever.SMS_RETRIEVED_ACTION == intent.action) {

                val extras = intent.extras?: return
                val smsRetrieverStatus = extras.parcelable<Status>(SmsRetriever.EXTRA_STATUS)?: return
                when (smsRetrieverStatus.statusCode) {
                    CommonStatusCodes.SUCCESS -> {
                        // Get consent intent
                        val consentIntent =
                            extras.parcelable<Intent>(SmsRetriever.EXTRA_CONSENT_INTENT)
                        try {
                            // Start activity to show consent dialog to user, activity must be started in
                            // 5 minutes, otherwise you'll receive another TIMEOUT intent
                            consentLauncher.launch(consentIntent)
                        } catch (e: ActivityNotFoundException) {
                            // Handle the exception ...
                            Toast.makeText(
                                this@MainActivity, "ActivityNotFound", Toast.LENGTH_SHORT
                            ).show()

                        }
                    }

                    CommonStatusCodes.TIMEOUT -> {
                        // Time out occurred, handle the error.
                        Toast.makeText(this@MainActivity, "Time out", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private val timer = object : CountDownTimer(
        TimeUnit.MINUTES.toMillis(5),
        TimeUnit.SECONDS.toMillis(1)
    ) {
        override fun onTick(millisUntilFinished: Long) {
            setTimerText(millisUntilFinished)
        }

        override fun onFinish() {
            setTimerText(0L)
        }
    }

    private val consentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (it.resultCode == Activity.RESULT_OK && it.data != null) {
            // Get SMS message content
            val message = it.data!!.getStringExtra(SmsRetriever.EXTRA_SMS_MESSAGE)
            // Extract one-time code from the message and complete verification
            // `message` contains the entire text of the SMS message, so you will need
            // to parse the string.
            binding.smsReceived.text = message
        } else {
            // Consent denied. User can type OTC manually.
            Toast.makeText(this@MainActivity, "Consent denied.", Toast.LENGTH_SHORT).show()

        }

    }

}