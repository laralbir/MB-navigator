package org.mbnavigator.android.auto

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.navigation.model.Maneuver
import androidx.core.graphics.drawable.IconCompat
import org.mbnavigator.android.core.routing.ManeuverType

/**
 * Utilidades de iconos vectoriales sobre Canvas para Android Auto (NavigationTemplate / Cluster)
 * y marcadores del mapa OSMDroid proyectado en la superficie del vehículo.
 */
object CarIconFactory {

    fun toCarManeuverType(type: ManeuverType): Int {
        return when (type) {
            ManeuverType.DEPART -> Maneuver.TYPE_DEPART
            ManeuverType.ARRIVE -> Maneuver.TYPE_DESTINATION
            ManeuverType.LEFT -> Maneuver.TYPE_TURN_NORMAL_LEFT
            ManeuverType.SLIGHT_LEFT -> Maneuver.TYPE_TURN_SLIGHT_LEFT
            ManeuverType.SHARP_LEFT -> Maneuver.TYPE_TURN_SHARP_LEFT
            ManeuverType.RIGHT -> Maneuver.TYPE_TURN_NORMAL_RIGHT
            ManeuverType.SLIGHT_RIGHT -> Maneuver.TYPE_TURN_SLIGHT_RIGHT
            ManeuverType.SHARP_RIGHT -> Maneuver.TYPE_TURN_SHARP_RIGHT
            ManeuverType.U_TURN -> Maneuver.TYPE_U_TURN_LEFT
            ManeuverType.ROUNDABOUT -> Maneuver.TYPE_ROUNDABOUT_ENTER_AND_EXIT_CCW
            ManeuverType.STRAIGHT -> Maneuver.TYPE_STRAIGHT
        }
    }

    fun buildManeuverCarIcon(type: ManeuverType, isOffRoute: Boolean = false): CarIcon {
        val sizePx = 128
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (isOffRoute) Color.parseColor("#EF4444") else Color.parseColor("#1A56DB")
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(RectF(4f, 4f, sizePx - 4f, sizePx - 4f), 26f, 26f, bgPaint)

        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 70f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val yCenter = sizePx / 2f - (fgPaint.descent() + fgPaint.ascent()) / 2f
        canvas.drawText(type.symbol, sizePx / 2f, yCenter, fgPaint)

        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    fun buildTextCarIcon(symbol: String, tintColor: Int = Color.WHITE): CarIcon {
        val sizePx = 72
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tintColor
            textSize = 44f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val y = sizePx / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(symbol, sizePx / 2f, y, paint)
        return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
    }

    fun primaryCarColor(): CarColor = CarColor.createCustom(
        Color.parseColor("#1A56DB"),
        Color.parseColor("#5FA8FF")
    )

    fun createMotoArrowDrawable(context: Context): Drawable {
        val size = (44 * context.resources.displayMetrics.density).toInt().coerceAtLeast(88)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f

        val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(72, 26, 86, 219)
            style = Paint.Style.FILL
        }
        canvas.drawCircle(cx, cy, size * 0.45f, haloPaint)

        val path = Path().apply {
            moveTo(cx, size * 0.12f)
            lineTo(size * 0.80f, size * 0.80f)
            lineTo(cx, size * 0.63f)
            lineTo(size * 0.20f, size * 0.80f)
            close()
        }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 6f
            strokeJoin = Paint.Join.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#1A56DB")
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, borderPaint)
        canvas.drawPath(path, fillPaint)
        return BitmapDrawable(context.resources, bitmap)
    }

    fun createBadgeDrawable(context: Context, label: String, colorInt: Int): Drawable {
        val size = (34 * context.resources.displayMetrics.density).toInt().coerceAtLeast(68)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = colorInt
            style = Paint.Style.FILL
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (label.length >= 3) size * 0.27f else size * 0.34f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        canvas.drawCircle(size / 2f, size / 2f, size * 0.46f, borderPaint)
        canvas.drawCircle(size / 2f, size / 2f, size * 0.40f, fillPaint)
        val y = size / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(label, size / 2f, y, textPaint)
        return BitmapDrawable(context.resources, bitmap)
    }
}
