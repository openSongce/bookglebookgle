package com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.link

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.PDFView
import com.ssafy.bookglebookgle.pdf.tools.pdf.viewer.model.LinkTapEvent

/**
 * 현재 PDF를 보여주는 뷰 객체로, 페이지 이동 등에 사용
 * */
class DefaultLinkHandler(private val pdfView: PDFView) : LinkHandler {

    override fun handleLinkEvent(event: LinkTapEvent?) {
        val uri = event?.link?.uri
        val page = event?.link?.destPageIdx
        if (!uri.isNullOrEmpty()) {
            handleUri(uri)
        } else page?.let { handlePage(it) }
    }

    private fun handleUri(uri: String) {
        val parsedUri = Uri.parse(uri)
        val intent = Intent(Intent.ACTION_VIEW, parsedUri)
        val context = pdfView.context
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Log.w(TAG, "No activity found for URI: $uri")
        }
    }

    // PDF 내부 페이지로 점프
    private fun handlePage(page: Int) {
        pdfView.jumpTo(page)
    }

    companion object {
        private val TAG = DefaultLinkHandler::class.java.simpleName
    }
}