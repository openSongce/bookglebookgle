package com.example.bookglebookgleserver.group.dto;

public record MyGroupSummaryDto(
        Long groupId,
        String title,
        String description,
        String imageUrl,
        String category,
        int currentMembers,
        int maxMembers
) {}

