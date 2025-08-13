package com.example.bookglebookgleserver.pdf.grpc;

import com.example.bookglebookgleserver.pdf.grpc.ReadingMode;

//src/main/java/com/example/bookglebookgleserver/pdf/grpc/GroupState.java

import com.example.bookglebookgleserver.pdf.grpc.Participant;
import io.grpc.stub.StreamObserver;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

class GroupState {
 final long groupId;
 final Map<String, ParticipantMeta> participants = new ConcurrentHashMap<>(); // userId -> meta
 final Map<String, StreamObserver<SyncMessage>> observers = new ConcurrentHashMap<>(); // userId -> stream
 
 final Map<String, Boolean> onlineByUser = new HashMap<>();
 final Map<String, Integer> progressByUser = new HashMap<>();
 
 volatile ReadingMode readingMode = ReadingMode.FREE;
 
 
 volatile String currentLeaderId = null;
 volatile int currentPage = 1;
 final ReentrantLock lock = new ReentrantLock();

 GroupState(long groupId) { this.groupId = groupId; }

 static class ParticipantMeta {
     final String userId;
     String userName = "";
     boolean isOriginalHost = false;
     String avatarKey = "";
     ParticipantMeta(String userId) { this.userId = userId; }

     Participant toProto(boolean isCurrentHost) {
         return Participant.newBuilder()
                 .setUserId(userId)
                 .setUserName(userName)
                 .setIsOriginalHost(isOriginalHost)
                 .setIsCurrentHost(isCurrentHost)
                 .setAvatarKey(avatarKey)
                 .build();
     }
 }
}
