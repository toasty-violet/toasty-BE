package com.toasty.domain.follow.repository;

/** 스토어별 팔로워 수. 스토어마다 세지 않고 한 번에 묶어 가져올 때 쓴다. */
public interface FollowerCount {

    Long getSellerId();

    long getFollowerCount();
}
