package com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.listener;

public interface OnErrorListener {

    /**
     * Called if error occurred while opening PDF
     * @param t Throwable with error
     */
    void onError(Throwable t);
}
