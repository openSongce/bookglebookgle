package com.ssafy.bookglebookgle.pdf.utils


import android.content.Context
import android.util.TypedValue

object Utils {

    fun convertDpToPixel(
        context: Context,
        dpValue: Float,
    ): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dpValue,
            context.resources.displayMetrics,
        ).toInt()
    }

}