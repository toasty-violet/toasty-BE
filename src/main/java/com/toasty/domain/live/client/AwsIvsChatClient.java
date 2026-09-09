package com.toasty.domain.live.client;

import com.toasty.domain.live.exception.LiveErrorCode;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.ivschat.IvschatClient;
import software.amazon.awssdk.services.ivschat.model.InternalServerException;
import software.amazon.awssdk.services.ivschat.model.ResourceNotFoundException;
import software.amazon.awssdk.services.ivschat.model.ThrottlingException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AwsIvsChatClient implements LiveChatClient {

    private final IvschatClient ivschatClient;

    @Override
    public String createRoom(String roomName) {
        try {
            return ivschatClient.createRoom(request -> request.name(roomName)).arn();
        } catch (Exception e) {
            if (TransientFailures.isTransient(
                    e, ThrottlingException.class, InternalServerException.class)) {
                log.warn("채팅방 생성 일시 실패 - roomName={}", roomName, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("채팅방 생성 실패 - roomName={}", roomName, e);
            throw new CustomException(LiveErrorCode.LIVE_CHAT_ROOM_CREATE_FAILED, e);
        }
    }

    // 이미 없는 방을 지우는 것은 실패가 아니다. 정리가 두 번 돌아도 통과해야 한다.
    @Override
    public void deleteRoom(String roomArn) {
        try {
            ivschatClient.deleteRoom(request -> request.identifier(roomArn));
        } catch (ResourceNotFoundException e) {
            log.debug("이미 없는 채팅방이라 삭제를 건너뛴다 - roomArn={}", roomArn);
        } catch (Exception e) {
            if (TransientFailures.isTransient(
                    e, ThrottlingException.class, InternalServerException.class)) {
                log.warn("채팅방 삭제 일시 실패 - roomArn={}", roomArn, e);
                throw new CustomException(LiveErrorCode.LIVE_STREAMING_TEMPORARILY_UNAVAILABLE, e);
            }
            log.error("채팅방 삭제 실패 - roomArn={}", roomArn, e);
            throw new CustomException(LiveErrorCode.LIVE_CHAT_ROOM_DELETE_FAILED, e);
        }
    }
}
