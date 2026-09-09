package com.toasty.domain.customer.service;

import com.toasty.domain.customer.controller.dto.response.CustomerProfileResponse;
import com.toasty.domain.customer.controller.dto.response.NicknameSearchResponse;
import com.toasty.domain.customer.controller.dto.response.NicknameSuggestionResponse;
import com.toasty.domain.customer.entity.Address;
import com.toasty.domain.customer.entity.Customer;
import com.toasty.domain.customer.entity.CustomerOnboardingCommand;
import com.toasty.domain.customer.entity.CustomerProfileUpdateCommand;
import com.toasty.domain.customer.exception.CustomerErrorCode;
import com.toasty.domain.customer.repository.AddressRepository;
import com.toasty.domain.customer.repository.CustomerRepository;
import com.toasty.global.exception.CustomException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    // 추천 닉네임은 이 셋을 이어 붙여 만든다. 가장 긴 조합도 닉네임 한도인 20자를 넘지 않는다
    private static final List<String> NICKNAME_ADJECTIVES =
            List.of("바삭한", "노릇한", "따끈한", "폭신한", "달콤한", "고소한", "포근한", "촉촉한");
    private static final List<String> NICKNAME_NOUNS =
            List.of("식빵", "크루아상", "바게트", "베이글", "마들렌", "스콘", "프레첼", "브리오슈");
    private static final int NICKNAME_SUFFIX_ORIGIN = 100;
    private static final int NICKNAME_SUFFIX_BOUND = 1000;

    // 이만큼 뽑아도 다 겹치면 조합을 포기하고 겹치지 않을 값으로 내려준다
    private static final int NICKNAME_SUGGESTION_ATTEMPTS = 10;
    private static final String FALLBACK_NICKNAME_PREFIX = "user_";

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;

    /** 온보딩 제출로 구매자 정보와 기본 배송지를 만든다. 유저의 역할 확정과 같은 트랜잭션에서 일어난다. */
    @Transactional
    public Customer createForOnboarding(CustomerOnboardingCommand command) {
        requireNicknameAvailable(command.nickname(), null);
        Customer customer = saveNicknameOrThrow(Customer.createForOnboarding(command));
        addressRepository.save(Address.createDefault(customer.getId(), command.address()));
        return customer;
    }

    /** 구매자 내 정보 화면에 쓸 닉네임·이름·연락처와 기본 배송지를 모은다. */
    @Transactional(readOnly = true)
    public CustomerProfileResponse getProfile(Long customerId) {
        return CustomerProfileResponse.of(
                findCustomer(customerId), findDefaultAddress(customerId).toDetail());
    }

    /** 내 정보 수정으로 구매자의 닉네임·이름·연락처와 기본 배송지를 바꾼다. */
    @Transactional
    public void updateProfile(CustomerProfileUpdateCommand command) {
        Customer customer = findCustomer(command.customerId());
        requireNicknameAvailable(command.nickname(), customer.getId());
        customer.updateProfile(command.nickname(), command.name(), command.phoneNumber());
        flushNicknameOrThrow();
        findDefaultAddress(command.customerId()).update(command.address());
    }

    /** 입력한 닉네임을 이미 다른 구매자가 쓰고 있는지 확인한다. 자기 닉네임을 그대로 둔 경우는 중복으로 보지 않는다. */
    @Transactional(readOnly = true)
    public NicknameSearchResponse searchNickname(String nickname, Long customerId) {
        return new NicknameSearchResponse(isNicknameTaken(nickname, customerId));
    }

    /** 온보딩 화면의 닉네임 입력창에 채워 둘 값을 만들어 준다. */
    // 저장하지 않으므로 유저가 제출하기 전에 다른 구매자가 먼저 쓸 수 있다. 그 경우는 온보딩 제출에서 중복으로 걸린다.
    @Transactional(readOnly = true)
    public NicknameSuggestionResponse suggestNickname() {
        for (int attempt = 0; attempt < NICKNAME_SUGGESTION_ATTEMPTS; attempt++) {
            String candidate = randomNickname();
            if (!customerRepository.existsByNickname(candidate)) {
                return new NicknameSuggestionResponse(candidate);
            }
        }
        return new NicknameSuggestionResponse(randomFallbackNickname());
    }

    /** 구매자가 탈퇴할 때 배송지를 지우고 닉네임을 놓아준다. 구매자 정보 자체는 거래 상대방 식별에 쓰여 남긴다. */
    @Transactional
    public void withdraw(Long customerId) {
        findCustomer(customerId).withdraw();
        addressRepository.deleteAllByCustomerId(customerId);
    }

    private Customer findCustomer(Long customerId) {
        return customerRepository
                .findById(customerId)
                .orElseThrow(() -> new CustomException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
    }

    private Address findDefaultAddress(Long customerId) {
        return addressRepository
                .findByCustomerIdAndIsDefaultTrue(customerId)
                .orElseThrow(
                        () -> new CustomException(CustomerErrorCode.CUSTOMER_ADDRESS_NOT_FOUND));
    }

    // customerId가 null이면 아직 구매자가 아닌 유저라 비교에서 뺄 자기 자신이 없다.
    private boolean isNicknameTaken(String nickname, Long customerId) {
        return customerId == null
                ? customerRepository.existsByNickname(nickname)
                : customerRepository.existsByNicknameAndIdNot(nickname, customerId);
    }

    private void requireNicknameAvailable(String nickname, Long customerId) {
        if (isNicknameTaken(nickname, customerId)) {
            throw new CustomException(CustomerErrorCode.CUSTOMER_NICKNAME_DUPLICATED);
        }
    }

    /**
     * 구매자를 DB에 바로 넣어, 검사와 저장 사이에 다른 구매자가 같은 닉네임을 선점했으면 닉네임 중복(409)으로 돌려준다.
     *
     * <p>배송지를 쓰기 전에 호출해야 한다. 그래야 여기서 나는 제약 위반이 uk_customers_nickname 하나로 좁혀진다.
     */
    private Customer saveNicknameOrThrow(Customer customer) {
        try {
            return customerRepository.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(CustomerErrorCode.CUSTOMER_NICKNAME_DUPLICATED, e);
        }
    }

    /**
     * 닉네임 변경만 DB에 먼저 반영해, 다른 구매자가 같은 닉네임을 선점했으면 닉네임 중복(409)으로 돌려준다.
     *
     * <p>배송지를 쓰기 전에 호출해야 한다. 그래야 여기서 나는 제약 위반이 uk_customers_nickname 하나로 좁혀진다.
     */
    private void flushNicknameOrThrow() {
        try {
            customerRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(CustomerErrorCode.CUSTOMER_NICKNAME_DUPLICATED, e);
        }
    }

    private String randomNickname() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        return NICKNAME_ADJECTIVES.get(random.nextInt(NICKNAME_ADJECTIVES.size()))
                + NICKNAME_NOUNS.get(random.nextInt(NICKNAME_NOUNS.size()))
                + random.nextInt(NICKNAME_SUFFIX_ORIGIN, NICKNAME_SUFFIX_BOUND);
    }

    private String randomFallbackNickname() {
        return FALLBACK_NICKNAME_PREFIX
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
