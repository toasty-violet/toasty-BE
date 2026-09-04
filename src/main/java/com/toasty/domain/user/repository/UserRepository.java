package com.toasty.domain.user.repository;

import com.toasty.domain.user.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByKakaoId(String kakaoId);

    boolean existsByNickname(String nickname);

    boolean existsByNicknameAndIdNot(String nickname, Long id);

    /**
     * 유저 인증에 필요한 값(유저 id, 유저 역할, 구매자/판매자 id)을 읽는다.
     *
     * <p>customers·sellers 중 한쪽만 매칭되고, 온보딩 전이면 둘 다 비어 있다.
     */
    @Query(
            value =
                    """
                    select u.id as userId, u.role as role, c.id as customerId, s.id as sellerId
                    from users u
                             left join customers c on c.user_id = u.id
                             left join sellers s on s.user_id = u.id
                    where u.id = :userId
                    """,
            nativeQuery = true)
    Optional<AuthUserProjection> findAuthUserById(@Param("userId") Long userId);
}
