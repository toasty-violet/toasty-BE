package com.toasty.domain.customer.service;

import com.toasty.domain.customer.entity.Address;
import com.toasty.domain.customer.entity.Customer;
import com.toasty.domain.customer.entity.CustomerOnboardingCommand;
import com.toasty.domain.customer.entity.CustomerProfile;
import com.toasty.domain.customer.exception.CustomerErrorCode;
import com.toasty.domain.customer.repository.AddressRepository;
import com.toasty.domain.customer.repository.CustomerRepository;
import com.toasty.global.exception.CustomException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final AddressRepository addressRepository;

    /** 온보딩 제출로 구매자 정보와 기본 배송지를 만든다. 유저의 역할 확정과 같은 트랜잭션에서 일어난다. */
    @Transactional
    public Customer createForOnboarding(CustomerOnboardingCommand command) {
        Customer customer = customerRepository.save(Customer.createForOnboarding(command));
        addressRepository.save(Address.createDefault(customer.getId(), command.address()));
        return customer;
    }

    /** 구매자의 이름·연락처와 기본 배송지를 조회한다. */
    @Transactional(readOnly = true)
    public CustomerProfile getProfile(Long customerId) {
        Customer customer =
                customerRepository
                        .findById(customerId)
                        .orElseThrow(
                                () -> new CustomException(CustomerErrorCode.CUSTOMER_NOT_FOUND));
        Address address =
                addressRepository
                        .findByCustomerIdAndIsDefaultTrue(customerId)
                        .orElseThrow(
                                () ->
                                        new CustomException(
                                                CustomerErrorCode.CUSTOMER_ADDRESS_NOT_FOUND));
        return new CustomerProfile(
                customer.getName(),
                customer.getPhoneNumber(),
                address.getPostalCode(),
                address.selectedAddress(),
                address.getDetailAddress());
    }

    /** 구매자가 탈퇴할 때 배송지를 지운다. 구매자 정보 자체는 거래 상대방 식별에 쓰여 남긴다. */
    @Transactional
    public void deleteAddresses(Long customerId) {
        addressRepository.deleteAllByCustomerId(customerId);
    }
}
