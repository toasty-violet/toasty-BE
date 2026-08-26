# Live API 계약

프론트엔드(`toasty-FE`, Next.js) 연동용 문서. 이슈 #3 범위까지 반영돼 있다.
**이 파일이 유일한 사본이다.** FE 저장소에 복사해 두지 않는다 — 두 벌을 두면 한쪽만 고쳐져 어긋난다.

- Base URL(로컬): `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- 요청·응답 본문은 모두 JSON (`Content-Type: application/json`)

## 인증 (임시)

현재 인증이 없다. 셀러 식별은 `X-Seller-Id` 헤더로 한다.

```
X-Seller-Id: 7
```

> **이 헤더는 임시 수단이다.** 서버가 값을 그대로 믿기 때문에 누구든 다른 셀러를 사칭할 수 있다.
> 카카오 로그인·JWT가 머지되면 이 헤더는 사라지고 `Authorization: Bearer ...`로 바뀐다.
> 프론트에서는 이 헤더를 넣는 지점을 한 곳(API 클라이언트 인터셉터 등)에 모아두면 교체가 쉽다.

`X-Seller-Id`가 없거나 숫자가 아니면 `401 COMMON_UNAUTHORIZED`가 나간다.

## 공통 응답 형태

모든 응답이 아래 껍데기로 감싸여 있다. `null` 필드는 직렬화에서 빠진다.

**성공**

```json
{
  "success": true,
  "data": { }
}
```

**실패**

```json
{
  "success": false,
  "error": {
    "code": "LIVE_NOT_FOUND",
    "message": "라이브를 찾을 수 없습니다."
  }
}
```

`error.message`는 사용자에게 그대로 보여줘도 되는 한글 문장이다. 내부 사정(AWS 오류 상세, 스택트레이스)은 담기지 않는다.
분기 처리는 `message`가 아니라 **`error.code`로** 한다.

**입력값 검증 실패**는 `fields`가 추가로 붙는다.

```json
{
  "success": false,
  "error": {
    "code": "COMMON_INVALID_INPUT",
    "message": "입력값이 올바르지 않습니다.",
    "fields": [
      { "field": "title", "message": "제목은 필수입니다." }
    ]
  }
}
```

## 1. 라이브 생성

```
POST /api/v1/lives
```

IVS 채널을 만들고 라이브를 `READY` 상태로 저장한다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| `X-Seller-Id` | O | 셀러 번호 |
| `Content-Type` | O | `application/json` |

**요청 본문**

| 필드 | 타입 | 필수 | 제약 |
| --- | --- | --- | --- |
| `title` | string | O | 공백 불가, 최대 100자 |
| `description` | string | X | 최대 1000자 |

```json
{
  "title": "빈티지 여름옷 라이브",
  "description": "여름 상품을 소개합니다"
}
```

**응답 `200`**

```json
{
  "success": true,
  "data": {
    "live": {
      "liveId": 5,
      "publicId": "17c062d4-bdae-4396-a2ec-4c51e54a369a",
      "sellerId": 7,
      "title": "빈티지 여름옷 라이브",
      "description": "여름 상품을 소개합니다",
      "status": "READY",
      "playbackUrl": "https://xxxx.ap-northeast-2.playback.live-video.net/api/video/v1/....m3u8",
      "createdAt": "2026-08-26T01:50:04.295641"
    },
    "broadcastCredential": {
      "ingestEndpoint": "xxxx.global-contribute.live-video.net",
      "streamKey": "sk_ap-northeast-2_..."
    }
  }
}
```

> ### ⚠️ `streamKey`는 이 응답에서만 받을 수 있다
>
> 서버는 스트림 키를 **저장하지 않는다.** 이 응답을 놓쳤다면 다시 조회할 방법이 없고,
> 3번 재발급 API로 새로 받아야 한다.
>
> 스트림 키는 방송 권한 그 자체다. 로그·`localStorage`·URL 쿼리스트링에 남기지 말고,
> 송출 시작 직전까지 메모리에만 들고 있는다.

**에러**

| 상태 | `code` | 상황 |
| --- | --- | --- |
| `400` | `COMMON_INVALID_INPUT` | `title` 누락·길이 초과 등 |
| `401` | `COMMON_UNAUTHORIZED` | `X-Seller-Id` 없음 또는 숫자 아님 |
| `502` | `LIVE_CHANNEL_CREATE_FAILED` | 채널 생성 실패. **재시도해도 똑같이 실패한다.** 서버 설정·권한 문제이므로 백엔드에 알린다 |
| `503` | `LIVE_STREAMING_TEMPORARILY_UNAVAILABLE` | 일시적 장애. **잠시 후 재시도하면 성공할 수 있다.** 재시도 버튼을 보여주기 적절한 케이스 |

502와 503을 구분해서 다루면 좋다. 503만 재시도를 유도하고, 502는 재시도해도 소용없으니 안내 문구를 다르게 준다.

## 2. 라이브 상세 조회

```
GET /api/v1/lives/{liveId}
```

인증 헤더가 필요 없다. 시청자도 호출할 수 있다.

**응답 `200`**

```json
{
  "success": true,
  "data": {
    "liveId": 5,
    "publicId": "17c062d4-bdae-4396-a2ec-4c51e54a369a",
    "sellerId": 7,
    "title": "빈티지 여름옷 라이브",
    "description": "여름 상품을 소개합니다",
    "status": "READY",
    "playbackUrl": "https://xxxx.ap-northeast-2.playback.live-video.net/api/video/v1/....m3u8",
    "createdAt": "2026-08-26T01:50:04.295641"
  }
}
```

`startedAt`·`endedAt`은 값이 있을 때만 내려온다. `READY` 상태에서는 아예 필드가 없다.

**이 응답에는 `streamKey`와 `ingestEndpoint`가 없다.** 공용 조회이므로 송출정보를 절대 담지 않는다.

**에러**

| 상태 | `code` | 상황 |
| --- | --- | --- |
| `404` | `LIVE_NOT_FOUND` | 해당 `liveId` 없음 |

## 3. 송출정보 재발급

```
POST /api/v1/lives/{liveId}/broadcast-credentials
```

**셀러 본인만 호출한다.** 기존 스트림 키를 폐기하고 새 키를 발급한다.

> 이전 키는 즉시 무효가 된다. **송출 중에 부르면 방송이 끊긴다.** 송출을 시작하기 직전에만 부른다.

**요청 헤더**

| 헤더 | 필수 | 설명 |
| --- | --- | --- |
| `X-Seller-Id` | O | 셀러 번호 |

**응답 `200`**

```json
{
  "success": true,
  "data": {
    "ingestEndpoint": "xxxx.global-contribute.live-video.net",
    "streamKey": "sk_ap-northeast-2_..."
  }
}
```

생성 때와 마찬가지로 **이 응답에서만 받을 수 있다.**

**에러**

| 상태 | `code` | 상황 |
| --- | --- | --- |
| `403` | `LIVE_FORBIDDEN` | 다른 셀러의 라이브 |
| `404` | `LIVE_NOT_FOUND` | 해당 `liveId` 없음 |
| `409` | `LIVE_ALREADY_ENDED` | 종료된 라이브는 재발급할 수 없다 |
| `409` | `LIVE_CREDENTIAL_REISSUE_CONFLICT` | 다른 재발급 요청이 처리 중. **잠시 후 재시도하면 성공한다** |
| `502` | `LIVE_CREDENTIAL_REISSUE_FAILED` | 재시도해도 실패한다. 백엔드에 알린다 |
| `503` | `LIVE_STREAMING_TEMPORARILY_UNAVAILABLE` | 일시적 장애. 재시도 유도 |

## 4. 송출 상태 조회

```
GET /api/v1/lives/{liveId}/stream-status
```

**셀러 본인만 호출한다.** IVS에 실제 송출 여부를 물어보고, 그 결과로 저장된 상태를 맞춘다.

**응답 `200`**

```json
{
  "success": true,
  "data": {
    "status": "LIVE",
    "broadcasting": true,
    "startedAt": "2026-08-26T02:14:31.882104"
  }
}
```

| 필드 | 의미 |
| --- | --- |
| `broadcasting` | IVS가 **지금** 송출을 받고 있는지 (실시간) |
| `status` | 서버에 저장된 상태 |

송출이 확인되면 `READY → LIVE`로 전이되고 `startedAt`이 **최초 한 번만** 기록된다.

> `broadcasting`이 `false`로 바뀌어도 **`status`를 `ENDED`로 내리지 않는다.**
> 송출이 잠깐 끊긴 것과 방송을 끝낸 것을 구분할 수 없기 때문이다.
> 네트워크가 튈 때마다 방송이 종료되면 곤란하다. 종료는 6번 API로만 한다.

**에러**

| 상태 | `code` | 상황 |
| --- | --- | --- |
| `403` | `LIVE_FORBIDDEN` | 다른 셀러의 라이브 |
| `404` | `LIVE_NOT_FOUND` | 해당 `liveId` 없음 |
| `409` | `LIVE_ALREADY_BROADCASTING` | 이 셀러가 이미 다른 라이브를 방송 중이다. 동시 방송은 1개로 제한된다 |
| `502` | `LIVE_STREAM_STATUS_FETCH_FAILED` | 재시도해도 실패한다 |
| `503` | `LIVE_STREAMING_TEMPORARILY_UNAVAILABLE` | 일시적 장애. 재시도 유도 |

## 5. 재생 정보 조회

```
GET /api/v1/lives/{liveId}/playback
```

**인증이 필요 없다.** 시청자용이며, IVS를 호출하지 않고 저장된 상태만 읽는다.

**응답 `200`**

```json
{
  "success": true,
  "data": {
    "playbackUrl": "https://xxxx.ap-northeast-2.playback.live-video.net/api/video/v1/....m3u8",
    "status": "LIVE"
  }
}
```

**에러**

| 상태 | `code` | 상황 |
| --- | --- | --- |
| `404` | `LIVE_NOT_FOUND` | 해당 `liveId` 없음 |

## 6. 방송 종료

```
POST /api/v1/lives/{liveId}/end
```

**셀러 본인만 호출한다.** 송출을 중단하고 스트림 키를 삭제한다.

채널과 `playbackUrl`은 **남긴다.** 지난 방송 페이지가 깨지지 않게 하기 위함이고,
송출하지 않는 유휴 채널에는 요금이 붙지 않는다. 대신 스트림 키를 지워
종료된 방송으로 다시 송출되는 것을 막는다.

**응답 `200`** — 상세 조회와 같은 형태이며 `status`가 `ENDED`, `endedAt`이 채워져 있다.

> **이미 종료된 라이브에 다시 요청해도 `200`이 나간다.** 멱등이므로 응답이 없거나
> 네트워크가 끊겼을 때 그냥 다시 보내면 된다.

**에러**

| 상태 | `code` | 상황 |
| --- | --- | --- |
| `403` | `LIVE_FORBIDDEN` | 다른 셀러의 라이브 |
| `404` | `LIVE_NOT_FOUND` | 해당 `liveId` 없음 |
| `502` | `LIVE_BROADCAST_STOP_FAILED` | 송출 중단 실패 |
| `502` | `LIVE_STREAM_KEY_DELETE_FAILED` | 키 삭제 실패 |
| `503` | `LIVE_STREAMING_TEMPORARILY_UNAVAILABLE` | 일시적 장애. 재시도 유도 |

502·503이 나면 **상태가 아직 `ENDED`가 아니다.** 다시 요청하면 그대로 이어진다.

## 폴링 가이드

방송 시작을 감지하는 방법이 역할마다 다르다.

| 역할 | 부를 API | AWS 호출 | 주기 |
| --- | --- | --- | --- |
| 셀러 (송출 화면) | `GET .../stream-status` | O | 3~5초 |
| 시청자 (대기 화면) | `GET .../playback` | X | 3~5초 |

> ### ⚠️ 시청자 화면에서 `stream-status`를 부르면 안 된다
>
> 이 API는 요청마다 AWS를 호출하므로 **시청자 수만큼 IVS API 호출이 늘어난다.**
> IVS는 계정 단위 쿼터가 있어서, 인기 방송 하나 때문에 계정 전체가 쓰로틀에 걸릴 수 있다.

셀러 화면의 `stream-status` 폴링이 저장된 상태를 `LIVE`로 바꾸고, 시청자는 `playback`에서
그 변화를 본다. 셀러가 창을 닫으면 갱신이 멈추지만, 그 경우 보여줄 방송도 없다.

## 필드 설명

| 필드 | 설명 |
| --- | --- |
| `liveId` | 내부 번호. API 호출에 쓴다 |
| `publicId` | 외부 공유용 UUID. 링크 노출에는 `liveId` 대신 이걸 쓴다 |
| `status` | `READY` / `LIVE` / `ENDED` |
| `playbackUrl` | IVS **Player SDK**에 넘길 재생 URL (HLS `.m3u8`) |
| `ingestEndpoint` | IVS **Web Broadcast SDK**에 넘길 송출 엔드포인트 |
| `streamKey` | 송출 권한을 가진 비밀값 |
| `startedAt` | 송출이 처음 확인된 시각. 최초 한 번만 기록된다 |
| `endedAt` | 방송 종료 시각 |
| `broadcasting` | 송출 상태 조회에만 있다. IVS의 실시간 송출 여부 |
| `createdAt` | ISO-8601, 타임존 없음 (`Asia/Seoul` 기준) |

> `status`는 **자동으로 `LIVE`가 되지 않는다.** 셀러 화면이 4번 송출 상태 조회를 호출해야
> 그 시점에 `READY → LIVE`로 전이된다. 종료는 6번 API를 명시적으로 불러야 한다.

## AWS IVS SDK 연동

- 송출: `amazon-ivs-web-broadcast` — `ingestEndpoint` + `streamKey`
- 시청: `amazon-ivs-player` — `playbackUrl`

두 값 모두 위 API 응답에서 온다. 프론트에 AWS 자격증명을 두지 않는다. 채널 조작은 서버만 한다.

송출 흐름은 이렇게 된다.

1. `POST /lives`로 라이브를 만들고 최초 송출정보를 받는다 (나중에 방송할 거라면 여기서 버려도 된다)
2. 방송 직전에 `POST .../broadcast-credentials`로 키를 새로 받는다
3. Web Broadcast SDK로 송출을 시작한다
4. `GET .../stream-status`를 폴링해 `broadcasting: true`를 확인한다 — 이때 `LIVE`로 전이된다
5. 방송을 마치면 `POST .../end`를 부른다

## 로컬 개발

백엔드를 먼저 띄운다.

```bash
docker compose -f docker/local/docker-compose.yml up -d
./gradlew bootRun
```

CORS는 `http://localhost:3000`이 기본 허용이다 (`CORS_ALLOWED_ORIGINS`).
`next dev`를 다른 포트로 띄우면 백엔드 `.env`의 `CORS_ALLOWED_ORIGINS`도 같이 바꿔야 한다.
`allowCredentials`가 켜져 있으므로 와일드카드 오리진은 쓸 수 없다.

**동작 확인**

```bash
curl -X POST http://localhost:8080/api/v1/lives \
  -H "Content-Type: application/json" \
  -H "X-Seller-Id: 7" \
  -d '{"title":"테스트 라이브","description":"설명"}'
```

> 서버에 AWS 자격증명이 없으면 `502 LIVE_CHANNEL_CREATE_FAILED`가 난다.
> 백엔드에서 `aws configure`가 돼 있어야 한다. `.env`에 AWS 키를 넣는 것으로는 동작하지 않는다
> (이 파일은 Spring 프로퍼티로만 로드되어 AWS SDK가 읽는 OS 환경변수가 되지 않는다).
