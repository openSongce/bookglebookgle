package com.example.bookglebookgleserver.pdf.grpc;

import java.util.concurrent.ConcurrentHashMap;

class GroupStore {
    private static final ConcurrentHashMap<Long, GroupState> STORE = new ConcurrentHashMap<>();
    static GroupState get(long groupId) {
        return STORE.computeIfAbsent(groupId, GroupState::new);
    }
}