package dev.slipaway.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import android.widget.Toast
import dev.slipaway.R
import dev.slipaway.call.CallTrigger
import dev.slipaway.data.Store

/**
 * Home-screen widget. A tap rings after the delay last chosen in the app (with the selected caller);
 * while a call is waiting it counts down, and another tap cancels.
 */
class RingWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { render(context, manager, it) }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        id: Int,
        newOptions: Bundle,
    ) = render(context, manager, id)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAP) return super.onReceive(context, intent)
        val store = Store.get(context)
        if (store.scheduled.value != null) {
            CallTrigger.cancel(context)
        } else {
            CallTrigger.describe(CallTrigger.schedule(context, store.settings.value.delaySeconds))?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }
        }
        updateAll(context)
    }

    companion object {
        private const val ACTION_TAP = "dev.slipaway.widget.TAP"

        /** Redraws every placed widget. Called whenever the delay, caller or waiting call changes. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            manager.getAppWidgetIds(ComponentName(context, RingWidget::class.java))
                .forEach { render(context, manager, it) }
        }

        private fun render(context: Context, manager: AppWidgetManager, id: Int) {
            val store = Store.get(context)
            val delay = store.settings.value.delaySeconds
            val caller = store.selectedCaller
            val waiting = store.scheduled.value
            val minWidth = manager.getAppWidgetOptions(id).getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val small = minWidth < 100

            val views = RemoteViews(context.packageName, if (small) R.layout.widget_ring_small else R.layout.widget_ring)
            views.setOnClickPendingIntent(android.R.id.background, tapIntent(context))

            val action = when {
                waiting != null -> "Cancel the call from ${caller.title}"
                delay <= 0 -> "Ring now with ${caller.title}"
                else -> "Ring in ${Store.formatDelay(delay)} with ${caller.title}"
            }
            views.setContentDescription(android.R.id.background, action)

            if (!small) {
                if (waiting != null) {
                    val left = (waiting.atMillis - System.currentTimeMillis()).coerceAtLeast(0)
                    views.setViewVisibility(R.id.widget_title, View.GONE)
                    views.setViewVisibility(R.id.widget_countdown, View.VISIBLE)
                    views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime() + left, "Ringing in %s", true)
                    views.setChronometerCountDown(R.id.widget_countdown, true)
                    views.setTextViewText(R.id.widget_subtitle, "${caller.title} · tap to cancel")
                } else {
                    views.setChronometer(R.id.widget_countdown, SystemClock.elapsedRealtime(), null, false)
                    views.setViewVisibility(R.id.widget_countdown, View.GONE)
                    views.setViewVisibility(R.id.widget_title, View.VISIBLE)
                    views.setTextViewText(
                        R.id.widget_title,
                        if (delay <= 0) "Ring now" else "Ring in ${Store.formatDelayShort(delay)}",
                    )
                    views.setTextViewText(R.id.widget_subtitle, caller.title)
                }
            }
            manager.updateAppWidget(id, views)
        }

        private fun tapIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(context, RingWidget::class.java).setAction(ACTION_TAP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        /** Asks the launcher to place the widget (Android 8+, where the launcher supports it). */
        fun requestPin(context: Context): Boolean {
            val manager = AppWidgetManager.getInstance(context) ?: return false
            if (!manager.isRequestPinAppWidgetSupported) return false
            return manager.requestPinAppWidget(ComponentName(context, RingWidget::class.java), null, null)
        }
    }
}
