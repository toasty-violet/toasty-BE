package com.toasty.domain.live.client;

import java.util.ArrayList;
import java.util.List;

/** 채팅방 생성·삭제를 기록만 하는 가짜 클라이언트. */
public class FakeLiveChatClient implements LiveChatClient {

    private final List<String> createdRoomNames = new ArrayList<>();
    private final List<String> deletedRoomArns = new ArrayList<>();
    private RuntimeException createFailure;
    private RuntimeException deleteFailure;

    @Override
    public String createRoom(String roomName) {
        if (createFailure != null) {
            throw createFailure;
        }
        createdRoomNames.add(roomName);
        return "arn:aws:ivschat:room/" + roomName;
    }

    @Override
    public void deleteRoom(String roomArn) {
        if (deleteFailure != null) {
            throw deleteFailure;
        }
        deletedRoomArns.add(roomArn);
    }

    public List<String> createdRoomNames() {
        return createdRoomNames;
    }

    public List<String> deletedRoomArns() {
        return deletedRoomArns;
    }

    public void failOnCreate(RuntimeException failure) {
        this.createFailure = failure;
    }

    public void failOnDelete(RuntimeException failure) {
        this.deleteFailure = failure;
    }
}
