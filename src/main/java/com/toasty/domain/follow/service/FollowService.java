package com.toasty.domain.follow.service;

import com.toasty.domain.follow.repository.FollowRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FollowService {

    private final FollowRepository followRepository;

    /** 구매자가 탈퇴할 때 그가 판매자를 팔로우한 관계를 모두 지운다. */
    @Transactional
    public void deleteByCustomerId(Long customerId) {
        followRepository.deleteAllByCustomerId(customerId);
    }

    /** 판매자가 탈퇴할 때 구매자가 그를 팔로우한 관계를 모두 지운다. */
    @Transactional
    public void deleteBySellerId(Long sellerId) {
        followRepository.deleteAllBySellerId(sellerId);
    }
}
