package com.ute.quanlydatban.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

// Định dạng tiền dùng chung: 357000 -> "357.000đ"
fun formatMoney(amount: Long): String {
    val symbols = DecimalFormatSymbols(Locale.US).apply { groupingSeparator = '.' }
    return DecimalFormat("#,###", symbols).format(amount) + "đ"
}