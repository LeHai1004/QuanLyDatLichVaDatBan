package com.ute.quanlydatban.ui.customer.booking

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.ute.quanlydatban.R
import com.ute.quanlydatban.data.model.DiningTable

class IndoorMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var currentFloor: Long = 2
    private var bookedTable: DiningTable? = null
    private var userLocation: PointF? = null
    private var userFloor: Long = 2
    private var routePoints: List<PointF> = emptyList()

    // Khởi tạo toàn bộ Paint, RectF, Path ngoài onDraw để đảm bảo 60fps
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = ContextCompat.getColor(context, R.color.payment_card_border)
    }

    private val corridorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.booktable_cream)
    }

    private val roomNormalBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.booktable_cream)
    }

    private val roomNormalStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(1f)
        color = ContextCompat.getColor(context, R.color.payment_card_border)
    }

    private val roomBookedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.checkin_room_badge_bg)
    }

    private val roomBookedStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
        color = ContextCompat.getColor(context, R.color.booktable_orange)
    }

    private val tableItemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.payment_card_border)
    }

    private val tableItemBookedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.booktable_orange)
    }

    private val stairsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(2f)
        color = ContextCompat.getColor(context, R.color.booktable_muted)
    }

    private val textHeaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.booktable_muted)
        textSize = spToPx(9f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textRoomNormalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.booktable_muted)
        textSize = spToPx(10f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val textRoomBookedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.booktable_active)
        textSize = spToPx(10f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dpToPx(3f)
        strokeCap = Paint.Cap.ROUND
        color = ContextCompat.getColor(context, R.color.booktable_orange)
        pathEffect = DashPathEffect(floatArrayOf(dpToPx(5f), dpToPx(4f)), 0f)
    }

    private val userHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.map_user_halo)
    }

    private val userDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.map_user_dot)
    }

    private val userDotCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val pinBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.booktable_orange)
    }

    private val pinCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    // Tái sử dụng đối tượng vẽ tránh cấp phát trong onDraw
    private val tempRect = RectF()
    private val tempTableRect = RectF()
    private val tempRoutePath = Path()
    private val tempPinPath = Path()

    fun setFloor(floor: Long) {
        if (currentFloor != floor) {
            currentFloor = floor
            invalidate()
        }
    }

    fun setMapData(
        bookedTable: DiningTable?,
        userLocation: PointF?,
        userFloor: Long,
        routePoints: List<PointF>
    ) {
        this.bookedTable = bookedTable
        this.userLocation = userLocation
        this.userFloor = userFloor
        this.routePoints = routePoints
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val padding = dpToPx(14f)

        // Vẽ khung bao ngoài bo góc 16dp
        tempRect.set(1f, 1f, w - 1f, h - 1f)
        canvas.drawRoundRect(tempRect, dpToPx(16f), dpToPx(16f), borderPaint)

        // Vẽ tiêu đề tầng góc trên
        val floorLabel = if (currentFloor == 1L) "TẦNG 1" else "TẦNG 2"
        canvas.drawText(floorLabel, padding, padding + dpToPx(10f), textHeaderPaint)

        if (currentFloor == 2L) {
            drawFloor2(canvas, w, h)
        } else {
            drawFloor1(canvas, w, h)
        }

        // 1. Vẽ đường nét đứt BFS chỉ đường
        drawRoute(canvas, w, h)

        // 2. Vẽ Ghim bàn đã đặt
        drawBookedPin(canvas, w, h)

        // 3. Vẽ Vị trí hiện tại của khách
        drawUserLocation(canvas, w, h)
    }

    private fun drawFloor2(canvas: Canvas, w: Float, h: Float) {
        // Hành lang chính dọc giữa
        tempRect.set(w * 0.38f, h * 0.08f, w * 0.62f, h * 0.88f)
        canvas.drawRoundRect(tempRect, dpToPx(6f), dpToPx(6f), corridorPaint)

        // 4 Phòng VIP
        drawRoom(canvas, "VIP 1", w * 0.05f, h * 0.12f, w * 0.35f, h * 0.40f, isBookedRoom("VIP 1"))
        drawRoom(canvas, "VIP 2", w * 0.65f, h * 0.12f, w * 0.95f, h * 0.40f, isBookedRoom("VIP 2"))
        drawRoom(canvas, "VIP 3", w * 0.05f, h * 0.44f, w * 0.35f, h * 0.72f, isBookedRoom("VIP 3"))
        drawRoom(canvas, "VIP 4", w * 0.65f, h * 0.44f, w * 0.95f, h * 0.72f, isBookedRoom("VIP 4"))

        // Cầu thang ở góc dưới trái
        drawStairs(canvas, w * 0.05f, h * 0.76f, w * 0.28f, h * 0.86f, "CẦU THANG")
    }

    private fun drawFloor1(canvas: Canvas, w: Float, h: Float) {
        // Khu vực sảnh chính
        tempRect.set(w * 0.15f, h * 0.15f, w * 0.85f, h * 0.78f)
        canvas.drawRoundRect(tempRect, dpToPx(8f), dpToPx(8f), corridorPaint)

        // Bàn 1
        drawTableItem(canvas, "Bàn 1", w * 0.20f, h * 0.30f, isBookedTable("Bàn 1"))
        // Bàn 2
        drawTableItem(canvas, "Bàn 2", w * 0.80f, h * 0.30f, isBookedTable("Bàn 2"))
        // Bàn 3
        drawTableItem(canvas, "Bàn 3", w * 0.20f, h * 0.68f, isBookedTable("Bàn 3"))

        // Cầu thang góc dưới trái nối lên tầng 2
        drawStairs(canvas, w * 0.05f, h * 0.76f, w * 0.28f, h * 0.86f, "LÊN TẦNG 2")

        // Cửa vào
        canvas.drawText("CỬA RA VÀO", w * 0.40f, h * 0.94f, textHeaderPaint)
    }

    private fun drawRoom(canvas: Canvas, name: String, l: Float, t: Float, r: Float, b: Float, isBooked: Boolean) {
        tempRect.set(l, t, r, b)
        val bgPaint = if (isBooked) roomBookedBgPaint else roomNormalBgPaint
        val strokePaint = if (isBooked) roomBookedStrokePaint else roomNormalStrokePaint
        canvas.drawRoundRect(tempRect, dpToPx(10f), dpToPx(10f), bgPaint)
        canvas.drawRoundRect(tempRect, dpToPx(10f), dpToPx(10f), strokePaint)

        // Bàn và ghế bên trong
        val cx = (l + r) / 2f
        val cy = (t + b) / 2f
        tempTableRect.set(cx - dpToPx(18f), cy - dpToPx(12f), cx + dpToPx(18f), cy + dpToPx(12f))
        val itemPaint = if (isBooked) tableItemBookedPaint else tableItemPaint
        canvas.drawRoundRect(tempTableRect, dpToPx(6f), dpToPx(6f), itemPaint)

        // Tên phòng
        val textPaint = if (isBooked) textRoomBookedPaint else textRoomNormalPaint
        canvas.drawText(name, l + dpToPx(8f), t + dpToPx(16f), textPaint)
    }

    private fun drawTableItem(canvas: Canvas, name: String, cx: Float, cy: Float, isBooked: Boolean) {
        val size = dpToPx(28f)
        tempRect.set(cx - size, cy - size, cx + size, cy + size)
        val bgPaint = if (isBooked) roomBookedBgPaint else roomNormalBgPaint
        val strokePaint = if (isBooked) roomBookedStrokePaint else roomNormalStrokePaint
        canvas.drawRoundRect(tempRect, dpToPx(8f), dpToPx(8f), bgPaint)
        canvas.drawRoundRect(tempRect, dpToPx(8f), dpToPx(8f), strokePaint)

        val textPaint = if (isBooked) textRoomBookedPaint else textRoomNormalPaint
        canvas.drawText(name, cx - dpToPx(14f), cy + dpToPx(4f), textPaint)
    }

    private fun drawStairs(canvas: Canvas, l: Float, t: Float, r: Float, b: Float, label: String) {
        val stepCount = 5
        val stepW = (r - l) / stepCount
        for (i in 0..stepCount) {
            val x = l + i * stepW
            canvas.drawLine(x, t, x, b, stairsPaint)
        }
        canvas.drawLine(l, t, r, t, stairsPaint)
        canvas.drawLine(l, b, r, b, stairsPaint)
        canvas.drawText(label, l, b + dpToPx(14f), textHeaderPaint)
    }

    private fun drawRoute(canvas: Canvas, w: Float, h: Float) {
        if (routePoints.size < 2) return

        tempRoutePath.reset()
        val first = routePoints.first()
        tempRoutePath.moveTo(first.x * w / 100f, first.y * h / 100f)
        for (i in 1 until routePoints.size) {
            val pt = routePoints[i]
            tempRoutePath.lineTo(pt.x * w / 100f, pt.y * h / 100f)
        }
        canvas.drawPath(tempRoutePath, routePaint)
    }

    private fun drawBookedPin(canvas: Canvas, w: Float, h: Float) {
        val table = bookedTable ?: return
        if (table.floor != currentFloor) return

        val px = (table.mapX.toFloat() * w / 100f)
        val py = (table.mapY.toFloat() * h / 100f)

        // Vẽ pin hình giọt nước
        tempPinPath.reset()
        val pinH = dpToPx(24f)
        val pinR = dpToPx(10f)
        tempPinPath.moveTo(px, py)
        tempPinPath.quadTo(px - pinR, py - pinH * 0.6f, px - pinR, py - pinH + pinR)
        tempPinPath.addCircle(px, py - pinH + pinR, pinR, Path.Direction.CW)
        tempPinPath.lineTo(px, py)
        canvas.drawPath(tempPinPath, pinBodyPaint)

        // Chấm tròn trắng ở giữa ghim
        canvas.drawCircle(px, py - pinH + pinR, dpToPx(3.5f), pinCenterPaint)
    }

    private fun drawUserLocation(canvas: Canvas, w: Float, h: Float) {
        val loc = userLocation ?: return
        if (userFloor != currentFloor) return

        val cx = loc.x * w / 100f
        val cy = loc.y * h / 100f

        // Vòng tròn halo tỏa nhẹ xung quanh
        canvas.drawCircle(cx, cy, dpToPx(15f), userHaloPaint)
        // Chấm xanh
        canvas.drawCircle(cx, cy, dpToPx(7f), userDotPaint)
        // Chấm trắng nhỏ tâm
        canvas.drawCircle(cx, cy, dpToPx(2.5f), userDotCenterPaint)

        // Dòng chữ "Bạn đang ở đây"
        canvas.drawText("Bạn đang ở đây", cx - dpToPx(28f), cy + dpToPx(22f), textHeaderPaint)
    }

    private fun isBookedRoom(roomName: String): Boolean {
        val name = bookedTable?.name ?: return false
        return name.contains(roomName, ignoreCase = true)
    }

    private fun isBookedTable(tableName: String): Boolean {
        val name = bookedTable?.name ?: return false
        return name.contains(tableName, ignoreCase = true)
    }

    private fun dpToPx(dp: Float): Float = dp * resources.displayMetrics.density
    private fun spToPx(sp: Float): Float = sp * resources.displayMetrics.scaledDensity
}
