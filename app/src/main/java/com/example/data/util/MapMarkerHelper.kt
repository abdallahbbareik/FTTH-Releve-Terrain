package com.example.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.example.data.local.FtthNodeType
import com.example.data.local.NodeConformity

object MapMarkerHelper {

    fun createNodeMarkerDrawable(
        context: Context,
        type: FtthNodeType,
        conformity: NodeConformity,
        sizeDp: Int = 46
    ): Drawable {
        val density = context.resources.displayMetrics.density
        val px = (sizeDp * density).toInt()

        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val baseColor = when (type) {
            FtthNodeType.POTEAU -> Color.parseColor("#EA580C")   // Orange
            FtthNodeType.CHAMBRE -> Color.parseColor("#16A34A")  // Vert
            FtthNodeType.BOITIER -> Color.parseColor("#9333EA")  // Violet
            FtthNodeType.SRO -> Color.parseColor("#DC2626")      // Rouge vif
            FtthNodeType.IMMEUBLE -> Color.parseColor("#0284C7") // Bleu ciel
            FtthNodeType.VILLA -> Color.parseColor("#DB2777")    // Rose
        }

        val radius = px / 2f
        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = baseColor
            style = Paint.Style.FILL
        }

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (conformity == NodeConformity.NON_CONFORME) Color.parseColor("#EF4444") else Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }

        canvas.drawCircle(radius, radius, radius - (3f * density), circlePaint)
        canvas.drawCircle(radius, radius, radius - (3f * density), borderPaint)

        // Draw initial letter
        val letter = when (type) {
            FtthNodeType.POTEAU -> "P"
            FtthNodeType.CHAMBRE -> "C"
            FtthNodeType.BOITIER -> "B"
            FtthNodeType.SRO -> "S"
            FtthNodeType.IMMEUBLE -> "I"
            FtthNodeType.VILLA -> "V"
        }

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 14f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val textY = radius - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(letter, radius, textY, textPaint)

        // Non-conforme alert dot if applicable
        if (conformity == NodeConformity.NON_CONFORME) {
            val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#B91C1C")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(px - (6f * density), 6f * density, 4f * density, alertPaint)
        }

        return BitmapDrawable(context.resources, bitmap)
    }

    fun createTechnicianLocationDrawable(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val px = (32 * density).toInt()

        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = px / 2f

        // Outer glow
        val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B82F6")
            alpha = 70
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius - 1f, glowPaint)

        // Inner solid circle
        val innerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#2563EB")
            style = Paint.Style.FILL
        }
        canvas.drawCircle(radius, radius, radius * 0.55f, innerPaint)

        // White border
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        canvas.drawCircle(radius, radius, radius * 0.55f, borderPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    fun createVertexDrawable(context: Context, number: Int, isSelected: Boolean): Drawable {
        val density = context.resources.displayMetrics.density
        val px = (24 * density).toInt()

        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = px / 2f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.parseColor("#EAB308") else Color.parseColor("#2563EB")
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        canvas.drawCircle(radius, radius, radius - (2f * density), circlePaint)
        canvas.drawCircle(radius, radius, radius - (2f * density), borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isSelected) Color.BLACK else Color.WHITE
            textSize = 10f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = radius - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText(number.toString(), radius, textY, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }

    fun createTrackPhotoDrawable(context: Context): Drawable {
        val density = context.resources.displayMetrics.density
        val px = (26 * density).toInt()

        val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val radius = px / 2f

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0284C7") // Cyan/Bleu photo
            style = Paint.Style.FILL
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        canvas.drawCircle(radius, radius, radius - (2f * density), circlePaint)
        canvas.drawCircle(radius, radius, radius - (2f * density), borderPaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 12f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val textY = radius - ((textPaint.descent() + textPaint.ascent()) / 2f)
        canvas.drawText("📷", radius, textY, textPaint)

        return BitmapDrawable(context.resources, bitmap)
    }
}
