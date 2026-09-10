package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.ChatToken;
import com.toasty.domain.live.client.dto.ChatTokenCommand;

/** 라이브 채팅 인프라 경계. 서버는 방을 만들고 없애기만 하고 메시지는 다루지 않는다. */
public interface LiveChatClient {

    /** 방을 만들고 ARN을 돌려준다. */
    String createRoom(String roomName);

    void deleteRoom(String roomArn);

    /** 시청자가 채팅방에 붙을 때 쓸 토큰을 발급한다. */
    ChatToken createToken(ChatTokenCommand command);
}
