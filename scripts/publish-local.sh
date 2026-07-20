#!/usr/bin/env bash
#
# publish-local.sh — GitHub Action(.github/workflows/publish.yml)의 로컬 재현판.
#
# LMBridge를 빌드해 Maven 산출물을 만들고, r00tIsRoot/packages 저장소의
# gh-pages 브랜치(= https://r00tisroot.github.io/packages/ Maven 저장소)로 배포한다.
# CI가 PAT_TOKEN을 쓰는 자리를 로컬에서는 사용자의 git 자격증명이 대신한다.
#
# 사용:
#   ./scripts/publish-local.sh            # 빌드 → 복사 → commit → push
#   DRY_RUN=1 ./scripts/publish-local.sh  # push 직전까지만(배포 안 함, 검증용)
#
# 환경변수:
#   PACKAGES_DIR  packages 저장소를 클론/재사용할 경로 (기본: ~/.cache/lmbridge-packages)
#   DRY_RUN=1     push 생략
set -euo pipefail

GROUP_PATH="com/isroot/lmbridge"
LOCAL_REPO="lmbridge/build/repo/${GROUP_PATH}"
PACKAGES_REMOTE="https://github.com/r00tIsRoot/packages.git"
PACKAGES_DIR="${PACKAGES_DIR:-$HOME/.cache/lmbridge-packages}"
DRY_RUN="${DRY_RUN:-0}"

# 저장소 루트로 이동(scripts/의 상위)
cd "$(dirname "$0")/.."

echo "==> 1) Gradle publish (로컬 저장소로)"
./gradlew clean :lmbridge:publishReleasePublicationToLocalRepository --no-daemon

echo "==> 2) POM 산출물에서 버전 추출"
VERSION="$(ls "${LOCAL_REPO}" | grep -E '^[0-9]' | head -1)"
if [ -z "${VERSION}" ]; then
  echo "!! ${LOCAL_REPO} 에서 버전 디렉터리를 찾지 못했습니다." >&2
  exit 1
fi
echo "    version = ${VERSION}"

# 태그 기반 자동 실행 시, 태그 버전(vX.Y.Z)과 build.gradle 버전이 일치하는지 검증
if [ -n "${EXPECT_VERSION:-}" ] && [ "${EXPECT_VERSION}" != "${VERSION}" ]; then
  echo "!! 태그 버전(${EXPECT_VERSION})과 빌드 버전(${VERSION})이 다릅니다." >&2
  echo "   lmbridge/build.gradle.kts 의 version 을 ${EXPECT_VERSION} 로 맞추고 다시 태그하세요." >&2
  exit 1
fi

echo "==> 3) packages(gh-pages) 준비: ${PACKAGES_DIR}"
if [ -d "${PACKAGES_DIR}/.git" ]; then
  git -C "${PACKAGES_DIR}" fetch --quiet origin gh-pages
  git -C "${PACKAGES_DIR}" checkout --quiet gh-pages
  git -C "${PACKAGES_DIR}" reset --hard --quiet origin/gh-pages
else
  git clone --quiet --branch gh-pages --single-branch "${PACKAGES_REMOTE}" "${PACKAGES_DIR}"
fi

echo "==> 4) 산출물 복사 (버전 디렉터리만 — CI와 동일, maven-metadata는 건드리지 않음)"
DEST="${PACKAGES_DIR}/${GROUP_PATH}/${VERSION}"
mkdir -p "${DEST}"
cp -R "${LOCAL_REPO}/${VERSION}/." "${DEST}/"

echo "==> 5) commit & push"
cd "${PACKAGES_DIR}"
git add .
if git diff --staged --quiet; then
  echo "    변경 없음 — ${VERSION}는 이미 배포되어 있습니다. (버전을 올리세요)"
  exit 0
fi
git commit --quiet -m "Release v${VERSION}"
if [ "${DRY_RUN}" = "1" ]; then
  echo "    DRY_RUN=1 → push 생략. 아래에서 커밋 확인 후 수동 push 가능:"
  echo "      git -C '${PACKAGES_DIR}' push origin gh-pages"
else
  git push origin gh-pages
  echo "    배포 완료: https://r00tisroot.github.io/packages/${GROUP_PATH}/${VERSION}/"
fi
