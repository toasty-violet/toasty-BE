package com.toasty.domain.live.client;

import com.toasty.domain.live.client.dto.ChatToken;
import com.toasty.domain.live.client.dto.ChatTokenCommand;
import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.exception.CustomException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ivschat.IvschatClient;
import software.amazon.awssdk.services.ivschat.model.ChatTokenCapability;
import software.amazon.awssdk.services.ivschat.model.CreateChatTokenResponse;
import software.amazon.awssdk.services.ivschat.model.InternalServerException;
import software.amazon.awssdk.services.ivschat.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ivschat.model.ThrottlingException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsIvsChatClient implements LiveChatClient {

    // 이 서비스에서 다시 시도하면 풀릴 수 있는 실패들. 방 한도 초과는 다시 불러도 풀리지 않아 넣지 않는다.
    // IVS 기본값과 같지만, 기본값이 바뀌어도 흔들리지 않도록 명시한다. 만료되면 화면이 다시 발급받는다.
    private static final int SESSION_MINUTES = 60;

    private static final Class<?>[] TRANSIENT_TYPES = {
        ThrottlingException.class, InternalServerException.class
    };

    private final IvschatClient ivschatClient;

    @Override
    public String createRoom(String roomName) {
        try {
            return ivschatClient.createRoom(request -> request.name(roomName)).arn();
        } catch (Exception e) {
            if (isTransient(e)) {
                log.warn("채팅방 생성 일시 실패 - roomName={}", roomName, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("채팅방 생성 실패 - roomName={}", roomName, e);
            throw new CustomException(LiveErrorCode.LIVE_CHAT_ROOM_CREATE_FAILED, e);
        }
    }

    @Override
    public ChatToken createToken(ChatTokenCommand command) {
        try {
            CreateChatTokenResponse response =
                    ivschatClient.createChatToken(
                            request ->
                                    request.roomIdentifier(command.chatRoomArn())
                                            .userId(command.chatUserId())
                                            .capabilities(capabilitiesOf(command))
                                            .sessionDurationInMinutes(SESSION_MINUTES)
                                            .attributes(attributesOf(command)));
            return new ChatToken(response.token(), response.tokenExpirationTime());
        } catch (Exception e) {
            if (isTransient(e)) {
                log.warn("채팅 토큰 발급 일시 실패 - chatRoomArn={}", command.chatRoomArn(), e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("채팅 토큰 발급 실패 - chatRoomArn={}", command.chatRoomArn(), e);
            throw new CustomException(LiveErrorCode.LIVE_CHAT_TOKEN_ISSUE_FAILED, e);
        }
    }

    // 권한을 주지 않으면 읽기만 된다. 비로그인 시청자가 여기에 해당한다.
    private List<ChatTokenCapability> capabilitiesOf(ChatTokenCommand command) {
        return command.writable() ? List.of(ChatTokenCapability.SEND_MESSAGE) : List.of();
    }

    // 화면이 보낸 사람을 그리는 데 쓴다. 비로그인은 이름이 없어 역할만 싣는다.
    private Map<String, String> attributesOf(ChatTokenCommand command) {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("role", command.role().name());
        if (command.displayName() != null) {
            attributes.put("displayName", command.displayName());
        }
        return attributes;
    }

    // 이미 없는 방을 지우는 것은 실패가 아니다. 정리가 두 번 돌아도 통과해야 한다.
    @Override
    public void deleteRoom(String roomArn) {
        try {
            ivschatClient.deleteRoom(request -> request.identifier(roomArn));
        } catch (ResourceNotFoundException e) {
            log.debug("이미 없는 채팅방이라 삭제를 건너뛴다 - roomArn={}", roomArn);
        } catch (Exception e) {
            if (isTransient(e)) {
                log.warn("채팅방 삭제 일시 실패 - roomArn={}", roomArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("채팅방 삭제 실패 - roomArn={}", roomArn, e);
            throw new CustomException(LiveErrorCode.LIVE_CHAT_ROOM_DELETE_FAILED, e);
        }
    }

    private static boolean isTransient(Throwable e) {
        return TransientFailures.isTransient(e, TRANSIENT_TYPES);
    }
}
