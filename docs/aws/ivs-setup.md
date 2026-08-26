# Amazon IVS 설정

라이브 방송은 Amazon IVS(Interactive Video Service) 저지연 채널을 쓴다.
서버만 채널을 조작하고, 프론트에는 AWS 자격증명을 두지 않는다.

## IAM 최소 권한

서버가 실제로 호출하는 API는 8개다. 그 외 권한은 주지 않는다.

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "ToastyLiveIvs",
      "Effect": "Allow",
      "Action": [
        "ivs:CreateChannel",
        "ivs:DeleteChannel",
        "ivs:GetChannel",
        "ivs:ListStreamKeys",
        "ivs:CreateStreamKey",
        "ivs:DeleteStreamKey",
        "ivs:GetStream",
        "ivs:StopStream"
      ],
      "Resource": "*"
    }
  ]
}
```

| 권한 | 쓰이는 곳 |
| --- | --- |
| `CreateChannel` | 라이브 생성 |
| `DeleteChannel` | 라이브 저장이 실패했을 때 보상 삭제 |
| `GetChannel` | **송출정보 재발급** — `ingestEndpoint`는 스트림 키가 아니라 채널에 딸려 있어 따로 조회한다 |
| `ListStreamKeys` `DeleteStreamKey` `CreateStreamKey` | 재발급, 종료 시 키 삭제 |
| `GetStream` | 송출 상태 조회 |
| `StopStream` | 방송 종료 |

> `ivs:GetChannel`을 빠뜨리기 쉽다. 없으면 생성·조회는 되는데 **재발급만** `AccessDenied`로
> 실패해서 `502 LIVE_CREDENTIAL_REISSUE_FAILED`가 나간다.

`Resource`를 좁히려면 채널 ARN 패턴으로 제한할 수 있지만, 채널이 런타임에 만들어지고
계정 안에 다른 IVS 사용처가 없어 지금은 `*`로 둔다.

## 리전

`ap-northeast-2`(서울)를 쓴다. **IVS는 7개 리전에서만 제공된다.**
미지원 리전을 넣으면 채널 생성이 실패한다.

## 로컬 개발 설정

자격증명은 `aws configure`로 설정한다. IAM 사용자는 `toasty-local-dev`.

```bash
aws configure
# AWS Access Key ID, Secret Access Key, region(ap-northeast-2) 입력
aws sts get-caller-identity   # 확인
```

> ### ⚠️ `.env`에 AWS 키를 넣으면 동작하지 않는다
>
> 루트 `.env`는 Spring이 프로퍼티로만 읽는다. AWS SDK는 **OS 환경변수나
> `~/.aws/credentials`**를 보기 때문에 `.env`에 넣은 값은 SDK에 도달하지 않는다.
> 자격증명이 없으면 `502 LIVE_CHANNEL_CREATE_FAILED`가 난다.

`application.yml`에서 채널 옵션을 조정한다.

| 설정 | 기본값 | 설명 |
| --- | --- | --- |
| `aws.ivs.region` | `ap-northeast-2` | IVS 지원 리전만 가능 |
| `aws.ivs.channel-type` | `BASIC` | `STANDARD`는 화질이 좋지만 비싸다 |
| `aws.ivs.latency-mode` | `LOW` | 저지연 |

## 요금

**송출·시청 시간에만 과금된다.** 유휴 채널은 무료다.
그래서 방송을 종료할 때 채널은 남기고 스트림 키만 지운다 —
지난 방송 페이지의 `playbackUrl`이 깨지지 않으면서 비용도 들지 않는다.

## 스트림 키 제약

**채널당 스트림 키는 1개고 조정할 수 없다.** 덮어쓰기도 안 된다.
그래서 재발급은 `ListStreamKeys` → `DeleteStreamKey` → `CreateStreamKey` 순서로만 가능하다.

이 순서 때문에 동시에 재발급 요청이 오면 두 군데서 깨진다.

- 이미 지워진 키를 지우려 하면 `ResourceNotFound` → **무시한다.** 결과가 같다
- 지운 직후 다른 요청이 키를 먼저 만들었으면 `ServiceQuotaExceeded` →
  `409 LIVE_CREDENTIAL_REISSUE_CONFLICT`로 바꿔 클라이언트가 재시도하게 한다

## 실환경 검증 절차

단위 테스트는 우리가 **가정한** AWS 예외로 검증한다. 가정 자체가 맞는지는 실제로 불러봐야 안다.

```bash
docker compose -f docker/local/docker-compose.yml up -d
./gradlew bootRun
```

1. **채널 생성** — `POST /api/v1/lives`. `playbackUrl`·`ingestEndpoint`·`streamKey`가 오는지
2. **재발급** — `POST /api/v1/lives/{id}/broadcast-credentials`.
   앞서 받은 것과 **다른** `streamKey`가 오는지 (`ivs:GetChannel` 권한 확인 지점)
3. **미송출 상태** — 송출하지 않은 채로 `GET .../stream-status`.
   `broadcasting: false`, `status: READY`가 오고 **에러가 아닌지**
4. **송출 중 상태** — Web Broadcast SDK로 송출을 시작하고 다시 조회.
   `broadcasting: true`, `status: LIVE`, `startedAt` 기록 확인
5. **동시 방송 제한** — 같은 셀러로 라이브를 하나 더 만들어 송출한 뒤 조회.
   `409 LIVE_ALREADY_BROADCASTING`인지
6. **종료** — `POST .../end`. `status: ENDED`, `endedAt` 기록.
   AWS 콘솔에서 **스트림 키가 사라지고 채널은 남아 있는지**
7. **종료 멱등** — 같은 요청을 한 번 더. `200`이 그대로 나가는지
8. **뒷정리** — 검증용 채널을 콘솔에서 삭제한다. 유휴 채널은 무료지만 쌓이면 헷갈린다

> 실환경 검증에서 확인한 예외 타입이 코드의 가정과 다르면
> `AwsIvsStreamingClient.isTransient()` 판정과 테스트의 `일시_실패`/`영구_실패` 목록을 함께 고친다.
