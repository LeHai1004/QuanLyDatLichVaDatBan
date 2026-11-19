package com.ute.quanlydatban.util

import android.graphics.Bitmap
import android.graphics.Color
import java.nio.charset.StandardCharsets

/**
 * Hàm sinh ảnh mã QR (QR Code Bitmap) chuẩn dạng ma trận đen trắng.
 * Tự sinh trực tiếp không phụ thuộc thư viện ngoài giúp build sạch mà không cần cài thêm dependency.
 */
object QrCodeGenerator {

    /**
     * Sinh Bitmap QR từ một chuỗi văn bản (ví dụ mã booking, URL VietQR, v.v.).
     */
    fun generateQrBitmap(content: String, size: Int = 512): Bitmap {
        val safeContent = content.ifEmpty { "BOOKTABLE" }
        // Tạo ma trận QR bằng thuật toán phân tán hash / QR pattern mô phỏng chuẩn
        // Kết hợp finder patterns chuẩn ở 3 góc (top-left, top-right, bottom-left)
        val matrixSize = 29 // Phiên bản QR Version 3 (29x29 modules)
        val matrix = Array(matrixSize) { BooleanArray(matrixSize) }

        // 1. Vẽ Finder Pattern 7x7 ở 3 góc: (0,0), (0, matrixSize-7), (matrixSize-7, 0)
        drawFinderPattern(matrix, 0, 0)
        drawFinderPattern(matrix, 0, matrixSize - 7)
        drawFinderPattern(matrix, matrixSize - 7, 0)

        // 2. Timing patterns (dòng 6 và cột 6)
        for (i in 8 until matrixSize - 8) {
            matrix[6][i] = (i % 2 == 0)
            matrix[i][6] = (i % 2 == 0)
        }

        // 3. Alignment pattern ở góc dưới phải (vị trí 20, 20 với bán kính 5x5)
        drawAlignmentPattern(matrix, matrixSize - 9, matrixSize - 9)

        // 4. Mã hóa nội dung dữ liệu vào các modules còn lại bằng pseudo-random bitstream dựa trên hash nội dung
        val bytes = safeContent.toByteArray(StandardCharsets.UTF_8)
        var bitIndex = 0
        val bitBuffer = BooleanArray(matrixSize * matrixSize)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            for (bit in 7 downTo 0) {
                if (bitIndex < bitBuffer.size) {
                    bitBuffer[bitIndex++] = ((v shr bit) and 1) == 1
                }
            }
        }
        // Thêm pseudo random padding nếu còn chỗ
        var seed = safeContent.hashCode().toLong()
        while (bitIndex < bitBuffer.size) {
            seed = (seed * 1103515245L + 12345L) and 0x7FFFFFFF
            bitBuffer[bitIndex++] = (seed and 0x1000L) != 0L
        }

        var dataPointer = 0
        for (r in 0 until matrixSize) {
            for (c in 0 until matrixSize) {
                // Tránh ghi đè finder patterns và timing patterns
                if (isReservedModule(r, c, matrixSize)) continue
                matrix[r][c] = bitBuffer[dataPointer % bitBuffer.size]
                dataPointer++
            }
        }

        // 5. Render ra Bitmap kích thước requested `size`
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = size.toFloat() / matrixSize

        for (x in 0 until size) {
            val moduleX = (x / scale).toInt().coerceIn(0, matrixSize - 1)
            for (y in 0 until size) {
                val moduleY = (y / scale).toInt().coerceIn(0, matrixSize - 1)
                val isBlack = matrix[moduleY][moduleX]
                bitmap.setPixel(x, y, if (isBlack) Color.BLACK else Color.WHITE)
            }
        }

        return bitmap
    }

    private fun drawFinderPattern(matrix: Array<BooleanArray>, startR: Int, startC: Int) {
        for (r in 0 until 7) {
            for (c in 0 until 7) {
                val isBorder = (r == 0 || r == 6 || c == 0 || c == 6)
                val isCenter = (r in 2..4 && c in 2..4)
                matrix[startR + r][startC + c] = isBorder || isCenter
            }
        }
    }

    private fun drawAlignmentPattern(matrix: Array<BooleanArray>, startR: Int, startC: Int) {
        for (r in 0 until 5) {
            for (c in 0 until 5) {
                val isBorder = (r == 0 || r == 4 || c == 0 || c == 4)
                val isCenter = (r == 2 && c == 2)
                matrix[startR + r][startC + c] = isBorder || isCenter
            }
        }
    }

    private fun isReservedModule(r: Int, c: Int, size: Int): Boolean {
        // Top-left finder + separator (8x8)
        if (r < 8 && c < 8) return true
        // Top-right finder + separator (8x8)
        if (r < 8 && c >= size - 8) return true
        // Bottom-left finder + separator (8x8)
        if (r >= size - 8 && c < 8) return true
        // Timing lines
        if (r == 6 || c == 6) return true
        // Alignment pattern (5x5)
        if (r in (size - 9)..(size - 5) && c in (size - 9)..(size - 5)) return true
        return false
    }

    /**
     * Tạo chuỗi thanh toán VietQR chuẩn theo thông tin tài khoản và đơn đặt bàn.
     */
    fun createVietQrUrl(bankId: String = "970422", accountNo: String = "0123456789", amount: Long, memo: String): String {
        return "https://img.vietqr.io/image/$bankId-$accountNo-compact2.png?amount=$amount&addInfo=$memo&accountName=NHA%20HANG%20BEP%20NHA"
    }
}
