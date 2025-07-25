package com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.listener;

/**
 * Copyright 2017 Bartosz Schiller
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import android.view.MotionEvent;

import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.link.LinkHandler;
import com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.model.LinkTapEvent;

import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Dispatchers;

public class Callbacks {

    /**
     * PDF가 완전히 로드되었을 때 호출할 콜백
     */
    private OnLoadCompleteListener onLoadCompleteListener;

    /**
     * PDF 로딩 중 에러 발생 시 호출할 콜백
     */
    private OnErrorListener onErrorListener;

    /**
     * 특정 페이지 로딩 중 에러 발생 시 콜백
     */
    private OnPageErrorListener onPageErrorListener;

    /**
     * PDF 렌더링 완료 시 콜백
     */
    private OnRenderListener onRenderListener;

    /**
     * 페이지가 바뀔 때 콜백
     */
    private OnPageChangeListener onPageChangeListener;

    /**
     * 페이지가 스크롤될 때 콜백
     */
    private OnPageScrollListener onPageScrollListener;

    /**
     * 	PDF 위에 그림(예: 주석 등)을 그릴 때 콜백
     */
    private OnDrawListener onDrawListener;

    /**
     * 전체 페이지 그림 그릴 때 콜백
     * */
    private OnDrawListener onDrawAllListener;

    /**
     * 탭(클릭) 이벤트 발생 시 콜백
     */
    private OnTapListener onTapListener;

    /**
     * 롱프레스(길게 누르기) 이벤트 콜백
     */
    private OnLongPressListener onLongPressListener;

    /**
     * PDF 내 하이퍼링크 클릭 시 처리할 핸들러
     */
    private LinkHandler linkHandler;

    /**
     * 리스너 등록 및 호출: PDF가 다 로드되었을 때 loadComplete(pagesCount) 호출
     * */
    public void setOnLoadComplete(OnLoadCompleteListener onLoadCompleteListener) {
        this.onLoadCompleteListener = onLoadCompleteListener;
    }

    public void callOnLoadComplete(int pagesCount) {
        if (onLoadCompleteListener != null) {
            onLoadCompleteListener.loadComplete(pagesCount);
        }
    }

    /**
     *전체 로딩 오류와 페이지별 오류에 대응하는 콜백 설정 및 호출
     * */
    public void setOnError(OnErrorListener onErrorListener) {
        this.onErrorListener = onErrorListener;
    }

    public OnErrorListener getOnError() {
        return onErrorListener;
    }

    public void setOnPageError(OnPageErrorListener onPageErrorListener) {
        this.onPageErrorListener = onPageErrorListener;
    }

    public boolean callOnPageError(int page, Throwable error) {
        if (onPageErrorListener != null) {
            onPageErrorListener.onPageError(page, error);
            return true;
        }
        return false;
    }

    /**
     *문서 렌더링 완료, 페이지 변경, 스크롤 시 콜백 호출
     * */
    public void setOnRender(OnRenderListener onRenderListener) {
        this.onRenderListener = onRenderListener;
    }

    public void callOnRender(int pagesCount) {
        if (onRenderListener != null) {
            onRenderListener.onInitiallyRendered(pagesCount);
        }
    }

    public void setOnPageChange(OnPageChangeListener onPageChangeListener) {
        this.onPageChangeListener = onPageChangeListener;
    }

    public void callOnPageChange(int page, int pagesCount) {
        if (onPageChangeListener != null) {
            onPageChangeListener.onPageChanged(page, pagesCount);
        }
    }

    public void setOnPageScroll(OnPageScrollListener onPageScrollListener) {
        this.onPageScrollListener = onPageScrollListener;
    }

    public void callOnPageScroll(int currentPage, float offset) {
        if (onPageScrollListener != null) {
            onPageScrollListener.onPageScrolled(currentPage, offset);
        }
    }

    /**
     * PDF 위에 그림을 그릴 때 사용할 콜백 등록 (예: 주석, 하이라이트)
     * */
    public void setOnDraw(OnDrawListener onDrawListener) {
        this.onDrawListener = onDrawListener;
    }

    public OnDrawListener getOnDraw() {
        return onDrawListener;
    }

    public void setOnDrawAll(OnDrawListener onDrawAllListener) {
        this.onDrawAllListener = onDrawAllListener;
    }

    public OnDrawListener getOnDrawAll() {
        return onDrawAllListener;
    }

    /**
     * 사용자 터치 이벤트 대응
     * */
    public void setOnTap(OnTapListener onTapListener) {
        this.onTapListener = onTapListener;
    }

    public boolean callOnTap(MotionEvent event) {
        return onTapListener != null && onTapListener.onTap(event);
    }

    public void setOnLongPress(OnLongPressListener onLongPressListener) {
        this.onLongPressListener = onLongPressListener;
    }

    public void callOnLongPress(MotionEvent event) {
        if (onLongPressListener != null) {
            onLongPressListener.onLongPress(event);
        }
    }

    /**
     * PDF 내의 링크 클릭 시, LinkHandler를 통해 처리
     * */
    public void setLinkHandler(LinkHandler linkHandler) {
        this.linkHandler = linkHandler;
    }

    public void callLinkHandler(LinkTapEvent event) {
        if (linkHandler != null) {
            linkHandler.handleLinkEvent(event);
        }
    }
}
