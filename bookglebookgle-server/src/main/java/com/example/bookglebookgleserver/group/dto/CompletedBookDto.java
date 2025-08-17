package com.example.bookglebookgleserver.group.dto;

import com.example.bookglebookgleserver.group.entity.Group;
import lombok.*;



public class CompletedBookDto {
    private final String fileName;           // pdf_file.file_name
    private final Group.Category category;   // group.category (Enum)

    public CompletedBookDto(String fileName, Group.Category category) {
        this.fileName = fileName;
        this.category = category;
    }
    public String getFileName() { return fileName; }
    public Group.Category getCategory() { return category; }
}