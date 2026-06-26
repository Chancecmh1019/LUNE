package com.lune.app.domain.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.lune.app.domain.menstrual.MenstrualRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Android implementation of [ReportExportService].
 *
 * Renders the shared [ReportContent] block list into a long PNG via
 * [Bitmap] + [Canvas] and writes it directly to the device's public
 * Downloads folder (via MediaStore on API 29+ or
 * [Environment.DIRECTORY_DOWNLOADS] on older devices).
 *
 * The file is immediately visible in the device file manager and any
 * downloads notification drawer; no share sheet is required, though the
 * caller may choose to open one on top of this.
 */
class AndroidReportExportService(private val context: Context) : ReportExportService {

    override suspend fun exportCycleReport(
        records: List<MenstrualRecord>,
        language: String,
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            val strings = ExportStrings.forLanguage(language)
            val lines   = ReportContent.build(records, strings)
            val plan    = measure(lines)
            val bitmap  = renderBitmap(plan)
            val fileName = ReportContent.buildFileName(language)

            val displayPath = saveToDownloads(bitmap, fileName)
            bitmap.recycle()

            ExportResult.Success(displayLocation = displayPath)
        } catch (t: Throwable) {
            ExportResult.Failure(message = t.message ?: "Unknown error")
        }
    }

    // ---------- Measure pass ----------

    private fun measure(lines: List<ReportLine>): RenderPlan {
        val commands = mutableListOf<DrawCommand>()
        var cursorY: Float = ReportLayout.MarginTop.toFloat()
        var cardStart: Float? = null
        val measurePaint = Paint().apply {
            isAntiAlias = true
            typeface = Typeface.DEFAULT
        }

        for (line in lines) {
            when (line) {
                is ReportLine.Text -> {
                    val spec  = line.style.spec()
                    val inset = if (cardStart != null) ReportLayout.CardInset else 0f
                    val maxWidth =
                        ReportLayout.ImageWidth - ReportLayout.MarginLeft * 2 - inset
                    measurePaint.textSize    = spec.fontSize
                    measurePaint.isFakeBoldText = spec.bold
                    val wrapped    = wrap(line.text, maxWidth, measurePaint)
                    val lineHeight = spec.fontSize * spec.lineHeightMultiplier
                    val color = if (spec.muted) ReportLayout.ColorMuted else ReportLayout.ColorText
                    for (segment in wrapped) {
                        commands.add(
                            DrawCommand.Text(
                                text  = segment,
                                size  = spec.fontSize,
                                bold  = spec.bold,
                                color = color,
                                topY  = cursorY,
                                inset = inset,
                            )
                        )
                        cursorY += lineHeight
                    }
                }
                is ReportLine.Spacer -> cursorY += line.height
                ReportLine.HorizontalRule -> {
                    commands.add(DrawCommand.Rule(y = cursorY + ReportLayout.RuleThickness))
                    cursorY += ReportLayout.RuleThickness * 4
                }
                ReportLine.CardBegin -> {
                    cardStart = cursorY
                    cursorY  += ReportLayout.CardInnerPadding
                }
                ReportLine.CardEnd -> {
                    cursorY  += ReportLayout.CardInnerPadding
                    commands.add(
                        DrawCommand.Rect(
                            top          = cardStart!!,
                            bottom       = cursorY,
                            color        = ReportLayout.ColorCardBg,
                            cornerRadius = ReportLayout.CardCorner,
                        )
                    )
                    cardStart = null
                }
            }
        }

        val totalHeight = (cursorY + ReportLayout.MarginBottom).toInt()
        return RenderPlan(commands, totalHeight)
    }

    private fun wrap(text: String, maxWidth: Float, paint: Paint): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        for (rawLine in text.split('\n')) {
            if (rawLine.isEmpty()) { lines.add(""); continue }
            val current = StringBuilder()
            for (ch in rawLine) {
                val candidate = current.toString() + ch
                if (paint.measureText(candidate) > maxWidth && current.isNotEmpty()) {
                    lines.add(current.toString())
                    current.clear()
                }
                current.append(ch)
            }
            if (current.isNotEmpty()) lines.add(current.toString())
        }
        return lines
    }

    // ---------- Render pass ----------

    private fun renderBitmap(plan: RenderPlan): Bitmap {
        val bitmap = Bitmap.createBitmap(
            ReportLayout.ImageWidth,
            plan.totalHeight.coerceAtLeast(MIN_HEIGHT),
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        canvas.drawColor(ReportLayout.ColorBackground)

        val textPaint = Paint().apply { isAntiAlias = true; typeface = Typeface.DEFAULT }
        val fillPaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
        val strokePaint = Paint().apply { 
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = ReportLayout.ColorCardBorder
        }
        val rulePaint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }

        // Pass 1: card backgrounds and borders
        for (cmd in plan.commands) {
            if (cmd is DrawCommand.Rect) {
                fillPaint.color = cmd.color
                val left  = ReportLayout.MarginLeft - ReportLayout.CardHorizontalBleed
                val right = (ReportLayout.ImageWidth - ReportLayout.MarginLeft) + ReportLayout.CardHorizontalBleed
                canvas.drawRoundRect(left, cmd.top, right, cmd.bottom, cmd.cornerRadius, cmd.cornerRadius, fillPaint)
                canvas.drawRoundRect(left, cmd.top, right, cmd.bottom, cmd.cornerRadius, cmd.cornerRadius, strokePaint)
            }
        }
        // Pass 2: horizontal rules
        for (cmd in plan.commands) {
            if (cmd is DrawCommand.Rule) {
                rulePaint.color = ReportLayout.ColorRuleBg
                canvas.drawRect(
                    ReportLayout.MarginLeft.toFloat(),
                    cmd.y,
                    (ReportLayout.ImageWidth - ReportLayout.MarginLeft).toFloat(),
                    cmd.y + ReportLayout.RuleThickness,
                    rulePaint,
                )
            }
        }
        // Pass 3: text
        for (cmd in plan.commands) {
            if (cmd is DrawCommand.Text) {
                textPaint.textSize      = cmd.size
                textPaint.color         = cmd.color
                textPaint.isFakeBoldText = cmd.bold
                val baseline = cmd.topY + cmd.size
                canvas.drawText(cmd.text, ReportLayout.MarginLeft + cmd.inset, baseline, textPaint)
            }
        }
        return bitmap
    }

    // ---------- Save to Downloads ----------

    private fun saveToDownloads(bitmap: Bitmap, fileName: String): String {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveViaMediaStore(bitmap, fileName)
        } else {
            saveLegacy(bitmap, fileName)
        }
    }

    private fun saveViaMediaStore(bitmap: Bitmap, fileName: String): String {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: error("MediaStore insert returned null URI")

        resolver.openOutputStream(uri)?.use { os ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }

        return "Downloads/$fileName"
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(bitmap: Bitmap, fileName: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        dir.mkdirs()
        val file = File(dir, fileName)
        FileOutputStream(file).use { os ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
            os.flush()
        }
        return file.absolutePath
    }

    // ---------- Models ----------

    private data class RenderPlan(val commands: List<DrawCommand>, val totalHeight: Int)

    private sealed class DrawCommand {
        data class Text(
            val text: String,
            val size: Float,
            val color: Int,
            val bold: Boolean,
            val topY: Float,
            val inset: Float,
        ) : DrawCommand()

        data class Rect(
            val top: Float,
            val bottom: Float,
            val color: Int,
            val cornerRadius: Float,
        ) : DrawCommand()

        data class Rule(val y: Float) : DrawCommand()
    }

    companion object {
        private const val MIN_HEIGHT = 800
    }
}
