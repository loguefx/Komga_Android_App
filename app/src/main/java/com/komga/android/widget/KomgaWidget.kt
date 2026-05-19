package com.komga.android.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.komga.android.MainActivity

/** Key constants shared with NewChapterWorker for widget data updates. */
const val WIDGET_PREFS = "komga_widget_prefs"
const val WIDGET_KEY_BOOKS = "on_deck_books"   // newline-separated list of book titles

// Widget palette — navy background matching the app icon
private val WidgetBg      = ColorProvider(Color(0xFF1A1F3C))
private val WidgetPrimary = ColorProvider(Color(0xFF6EC6FF))
private val WidgetText    = ColorProvider(Color.White)
private val WidgetSubText = ColorProvider(Color.White.copy(alpha = 0.65f))

class KomgaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val prefs = context.getSharedPreferences(WIDGET_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(WIDGET_KEY_BOOKS, "") ?: ""
        val books = raw.lines().filter { it.isNotBlank() }.take(5)

        provideContent {
            WidgetContent(context = context, books = books)
        }
    }
}

@Composable
private fun WidgetContent(context: Context, books: List<String>) {
    val openIntent = Intent(context, MainActivity::class.java).apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK
    }

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .padding(12.dp)
            .clickable(actionStartActivity(openIntent))
    ) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
            // ── Header row ────────────────────────────────────────────
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Komga",
                    style = TextStyle(
                        color = WidgetPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                )
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    text = "· On Deck",
                    style = TextStyle(color = WidgetSubText, fontSize = 13.sp)
                )
            }

            Spacer(GlanceModifier.height(8.dp))

            if (books.isEmpty()) {
                Text(
                    text = "Open the app to load your reading queue",
                    style = TextStyle(color = WidgetSubText, fontSize = 12.sp)
                )
            } else {
                books.forEach { title ->
                    Text(
                        text = "▸  $title",
                        style = TextStyle(color = WidgetText, fontSize = 12.sp),
                        maxLines = 1
                    )
                    Spacer(GlanceModifier.height(3.dp))
                }
            }
        }
    }
}

class KomgaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = KomgaWidget()
}
