package com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.link;

import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.LinkTapEvent;

public interface LinkHandler {

    /**
     * Called when link was tapped by user
     *
     * @param event current event
     */
    void handleLinkEvent(LinkTapEvent event);
}
