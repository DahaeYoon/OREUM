# 오름 (OREUM)

> 나의 목표를 향해 한 걸음씩, 함께 정상까지.

개인 학습 · 스터디 · 팀 프로젝트를 한곳에서 관리하는 Android 앱입니다.
사용자가 직접 입력한 데이터(할 일 예상 시간, 일정, 가능 시간대)로만 동작하며, AI가 작업을 자동 분석하지 않습니다.

## 개발 환경

| 항목 | 값 |
|---|---|
| Android Gradle Plugin | 9.4.1 (Kotlin 내장 지원) |
| Gradle | 9.6.0 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| 언어 · UI | Kotlin · XML Layout + ViewBinding (Compose 사용 안 함) |
| 구조 | Single Activity + Fragment, Navigation Component, MVVM + Repository |

## 패키지 구조

```
com.example.oreum
├─ MainActivity.kt          # NavHost + 하단 탭만 담당
├─ ui/                      # 화면 (Fragment + ViewModel)
│  ├─ auth  home  study  calendar  recovery
│  ├─ group  application  mypage
│  └─ common                # 여러 화면이 함께 쓰는 UI
├─ data/
│  ├─ model                 # 데이터 클래스 (Firestore 문서와 1:1)
│  ├─ repository            # 화면은 Repository만 호출 (Firebase 직접 호출 금지)
│  ├─ local                 # 테스트 데이터 · 로컬 저장
│  └─ remote                # Firebase Auth · Firestore
├─ domain/usecase           # 시간 부족 계산 · 일정 재배치 · 스터디 매칭 (Firebase와 분리된 순수 Kotlin)
└─ util/
```

## 개발 단계

STEP 0 프로젝트 구조 + GitHub → 1 테마·로그인 → 2 홈·산 진행률 → 3 내 학습 CRUD → 4 캘린더 → 5 위험 감지 → 6 경로 재탐색·승인 → 7 그룹 → 8 스터디 모집 → 9 가입신청서 → 10 역할 분담·그룹 알림 → 11 Firestore 권한·통합 테스트

---

## 팀 협업 규칙

### 브랜치
- `main` : 항상 빌드·실행되는 통합 코드. **직접 Push 금지**, Pull Request로만 병합
- `feature/<기능>` : 기능 개발 (예: `feature/auth`, `feature/home`, `feature/calendar`)
- 작업 시작 전 항상 `main`을 최신으로 받은 뒤 브랜치를 만듭니다.

### Commit 메시지 (Conventional Commits)
```
feat: add login screen
fix: resolve calendar crash
refactor: separate task repository
docs: update project README
chore: bump navigation version
```

### 같은 파일 충돌 줄이기
| 파일 | 규칙 |
|---|---|
| `gradle/libs.versions.toml`, `app/build.gradle.kts` | 라이브러리 추가는 **한 사람(빌드 담당)** 이 별도 PR(`chore: ...`)로 먼저 올리고, 병합된 뒤 각자 기능 브랜치에 반영 |
| `res/navigation/nav_graph.xml` | 새 화면은 PR 하나에 한 번에 추가. 같은 시간에 두 명이 수정하지 않도록 팀 채팅에 먼저 알리기. 기능이 커지면 기능별 `<include>` 그래프로 분리 |
| `values/strings.xml`, `colors.xml` | 화면별 접두어 사용 (`home_…`, `study_…`, `group_…`). 공용 색은 STEP 1 이후 추가하지 않고 기존 색 재사용 |
| `MainActivity.kt` | 화면 로직을 넣지 않기 (Fragment · ViewModel에만 작성) |
| 레이아웃 XML | 화면 하나 = 담당자 한 명. 남의 화면 XML을 고쳐야 하면 담당자와 먼저 이야기 |

### 비밀 정보
- `local.properties`, 서명 키(`*.jks`, `*.keystore`), Firebase **서비스 계정 키**(`*-adminsdk-*.json`)는 절대 커밋하지 않습니다 (`.gitignore`에 등록됨).
- `app/google-services.json`은 비밀 키가 아니라 앱 식별 설정이라, **Private 저장소**에서는 팀원이 같이 쓰도록 커밋해도 됩니다. 저장소를 Public으로 바꾸면 커밋에서 빼고 각자 Firebase 콘솔에서 받습니다.
- Firestore는 Security Rules 없이(테스트 모드로) 실제 운영하지 않습니다.
