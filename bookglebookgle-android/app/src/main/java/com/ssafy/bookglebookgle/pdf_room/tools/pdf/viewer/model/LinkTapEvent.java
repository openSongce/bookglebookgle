package com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model;

/**
 * Copyright 2016 Bartosz Schiller
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.graphics.RectF;

import com.shockwave.pdfium.PdfDocument;

/**
 * PDF 뷰어에서 사용자가 PDF 안의 하이퍼링크를 터치했을 때 발생하는 이벤트 정보를 담는 데이터 클래스
 * */
public class LinkTapEvent {
    private float originalX;
    private float originalY;
    private float documentX;
    private float documentY;
    private RectF mappedLinkRect;
    private PdfDocument.Link link;

    public LinkTapEvent(float originalX, float originalY, float documentX, float documentY, RectF mappedLinkRect, PdfDocument.Link link) {
        this.originalX = originalX;
        this.originalY = originalY;
        this.documentX = documentX;
        this.documentY = documentY;
        this.mappedLinkRect = mappedLinkRect;
        this.link = link;
    }

    public float getOriginalX() {
        return originalX;
    }

    public float getOriginalY() {
        return originalY;
    }

    public float getDocumentX() {
        return documentX;
    }

    public float getDocumentY() {
        return documentY;
    }

    public RectF getMappedLinkRect() {
        return mappedLinkRect;
    }

    public PdfDocument.Link getLink() {
        return link;
    }
}

