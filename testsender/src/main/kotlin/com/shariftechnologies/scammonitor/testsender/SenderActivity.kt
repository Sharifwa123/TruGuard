package com.shariftechnologies.scammonitor.testsender

import android.Manifest
import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Person
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Posts synthetic notifications from a package other than Scam Monitor, which is what makes the
 * real NotificationListenerService path testable (the monitor ignores its own notifications).
 */
class SenderActivity : Activity() {
    private var nextId = 1000

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        notificationManager()
            .createNotificationChannel(NotificationChannel(CHANNEL, "Synthetic test messages", NotificationManager.IMPORTANCE_DEFAULT))

        val pad = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        column.addView(TextView(this).apply {
            text = "Posts synthetic test notifications. Nothing here is real. Scam Monitor must have notification access enabled."
        })
        Scenarios.all.forEach { s ->
            column.addView(Button(this).apply {
                text = "${s.label}  →  ${s.expectation}"
                isAllCaps = false
                setOnClickListener { post(s.title, s.body) }
            })
        }
        column.addView(Button(this).apply {
            text = "Messaging-style (2 messages)"
            isAllCaps = false
            setOnClickListener { postMessaging() }
        })
        column.addView(Button(this).apply {
            text = "Burst: 12 notifications at once"
            isAllCaps = false
            setOnClickListener {
                repeat(12) { i ->
                    val s = Scenarios.all[i % Scenarios.all.size]
                    post(s.title, s.body + " (#$i)")
                }
            }
        })
        setContentView(ScrollView(this).apply { addView(column) })
    }

    private fun notificationManager(): NotificationManager = requireNotNull(getSystemService(NotificationManager::class.java))

    private fun post(title: String, body: String) {
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .build()
        notificationManager().notify(nextId++, n)
    }

    private fun postMessaging() {
        val person = Person.Builder().setName("Test Sender").build()
        val style = Notification.MessagingStyle(person)
            .addMessage("Hello, this is a test", System.currentTimeMillis() - 1000, person)
            .addMessage("Please send your MoMo PIN to verify your wallet", System.currentTimeMillis(), person)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setStyle(style)
            .build()
        notificationManager().notify(nextId++, n)
    }

    private companion object {
        const val CHANNEL = "synthetic_tests"
    }
}
