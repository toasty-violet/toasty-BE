package com.toasty.domain.customer.entity;

import com.toasty.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구매자의 배송지 Entity. 카카오 우편번호 서비스가 내려준 값과 유저가 입력한 상세주소를 담는다. 한 구매자가 여러 개를 가질 수 있고, 그중 하나가 기본 주소다.
 *
 * <p>카카오는 지번만 있는 주소에 도로명을 주지 않고 그 반대도 있어, 도로명·지번 둘 다 nullable로 둔다. 어느 쪽을 쓸지는 addressType으로 가른다.
 */
@Entity
@Getter
@Table(name = "addresses")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Address extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    // 우편번호 (zonecode)
    @Column(name = "postal_code", nullable = false, length = 10)
    private String postalCode;

    // 도로명 주소 (roadAddress)
    @Column(name = "road_address", length = 255)
    private String roadAddress;

    // 지번 주소 (jibunAddress)
    @Column(name = "jibun_address", length = 255)
    private String jibunAddress;

    // 유저가 도로명과 지번 중 무엇을 선택했는지 (userSelectedType)
    @Enumerated(EnumType.STRING)
    @Column(name = "address_type", nullable = false, length = 10)
    private AddressType addressType;

    // 건물명 (buildingName)
    @Column(name = "building_name", length = 100)
    private String buildingName;

    // 법정동·법정리 이름 (bname)
    @Column(name = "legal_dong", length = 50)
    private String legalDong;

    // 유저가 직접 입력한 상세주소
    @Column(name = "detail_address", length = 255)
    private String detailAddress;

    // 기본 주소 여부. 구매자당 하나만 true인 것은 Service에서 보장한다 (MySQL은 조건부 unique를 못 건다)
    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    private Address(
            Long customerId, CustomerOnboardingCommand.AddressCommand command, boolean isDefault) {
        this.customerId = customerId;
        this.postalCode = command.postalCode();
        this.roadAddress = command.roadAddress();
        this.jibunAddress = command.jibunAddress();
        this.addressType = command.addressType();
        this.buildingName = command.buildingName();
        this.legalDong = command.legalDong();
        this.detailAddress = command.detailAddress();
        this.isDefault = isDefault;
    }

    /** 온보딩에서 받은 첫 배송지. 다른 주소가 없으므로 기본 주소가 된다. */
    public static Address createDefault(
            Long customerId, CustomerOnboardingCommand.AddressCommand command) {
        return new Address(customerId, command, true);
    }
}
