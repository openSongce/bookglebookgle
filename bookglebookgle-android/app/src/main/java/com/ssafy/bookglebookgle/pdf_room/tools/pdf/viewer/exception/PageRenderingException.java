package com.ssafy.bookglebookgle.pdf_room.tools.pdf.viewer.exception;

public class PageRenderingException extends Exception {
  private final int page;

  public PageRenderingException(int page, Throwable cause) {
    super(cause);
    this.page = page;
  }

  public int getPage() {
    return page;
  }
}