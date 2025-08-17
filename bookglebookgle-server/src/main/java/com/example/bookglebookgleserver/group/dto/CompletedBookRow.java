package com.example.bookglebookgleserver.group.dto;

public interface CompletedBookRow {
    String getFileName();   // alias: fileName
    String getCategory();   // EnumType.STRING이라 DB에서 문자열로 반환
}