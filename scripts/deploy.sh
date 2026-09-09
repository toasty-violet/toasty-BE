#!/usr/bin/env bash
# 배포 서버에서 앱을 다시 띄운다. 저장소를 최신으로 맞춘 뒤에 실행한다.
#   bash scripts/deploy.sh
set -euo pipefail

COMPOSE_FILE="docker/prod/docker-compose.yml"
cd "$(dirname "$0")/.."

if [ ! -f docker/prod/.env ]; then
  echo "docker/prod/.env 가 없습니다. 비밀값을 먼저 채워주세요." >&2
  exit 1
fi

echo "== 이미지를 새로 만들고 띄운다 =="
docker compose -f "$COMPOSE_FILE" up -d --build

echo "== 기동을 기다린다 =="
for i in $(seq 1 60); do
  if docker compose -f "$COMPOSE_FILE" exec -T app \
      wget -qO- http://localhost:8080/actuator/health 2>/dev/null | grep -q '"status":"UP"'; then
    echo "정상 기동했습니다."
    # 이전 이미지가 쌓이면 디스크가 찬다.
    docker image prune -f >/dev/null
    exit 0
  fi
  sleep 5
done

echo "기동에 실패했습니다. 로그를 남깁니다." >&2
docker compose -f "$COMPOSE_FILE" logs --tail 100 app >&2
exit 1
