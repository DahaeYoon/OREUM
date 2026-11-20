# 오름 (OREUM)

> 나의 목표를 향해 한 걸음씩, 함께 정상까지

AI 기반 개인 적응형 학습 계획 및 실행 코칭 앱

학습 목표를 산으로 보고, 실제 학습 결과에 따라 AI가 다음 학습 구간을 제안하는 적응형 학습 코칭 Android 앱입니다.

- 핵심 흐름: 목표 설정 → AI 로드맵 · 1구간 생성 · 승인 → 구간 진행 · 결과 기록 → AI 다음 구간 추천 · 승인 → 정상 도착
- AI는 전체 로드맵(단계 · 범위, 날짜 없음)과 지금 할 구간만 만듭니다. 미래 날짜별 학습 일정은 만들지 않고, 실제로 공부한 날짜 · 시간만 기록합니다.
- AI 기능은 실제 AI API 없이 입력 데이터로 계산하는 **시뮬레이션**이며, 화면에도 시뮬레이션임을 표시합니다.
- AI 계획 · 추천은 사용자가 승인하기 전에는 자동으로 적용하지 않습니다. 이미 오른 진행률은 줄지 않습니다.
- 팀 · 그룹 기능은 없습니다. 캐릭터 · 보상 · 배지도 넣지 않습니다.

### 용어
| 용어 | 뜻 | 예시 |
|---|---|---|
| 목표(산) | 이루려는 학습 목표 | 데이터베이스 기말고사 |
| 단계 | 로드맵을 나눈 큰 덩어리 | 2단계 · 정규화 · 설계 |
| 구간 | AI가 한 번에 제안하는 학습 분량 (여러 날에 걸쳐 해도 됨) | 3구간 · BCNF와 반정규화 |


## 개발 환경

| 항목 | 값 |
|---|---|
| Android Gradle Plugin | 9.4.1 (Kotlin 내장 지원) |
| Gradle | 9.6.0 |
| compileSdk / targetSdk / minSdk | 37 / 37 / 26 |
| 언어 · UI | Kotlin · XML Layout + ViewBinding |
| 구조 | Single Activity + Fragment, Navigation Component, MVVM + Repository |

## 패키지 구조

```
com.example.oreum
├─ MainActivity.kt          # NavHost + 하단 탭만 담당
├─ ui/                      # 화면 (Fragment + ViewModel)
│  ├─ auth  home  mypage
│  ├─ study (목표 · 목표 상세)  aiplan (AI 로드맵 · 1구간)  round (결과 기록 · 다음 구간 추천 · 구간 상세)
│  ├─ records (기록)  mypage (마이)
│  └─ common                # 여러 화면이 함께 쓰는 UI
├─ data/
│  ├─ model                 # 데이터 클래스 (Firestore 문서와 1:1)
│  ├─ repository            # 화면은 Repository만 호출 (Firebase 직접 호출 금지)
│  ├─ local                 # 테스트 데이터 · 로컬 저장
│  └─ remote                # Firebase Auth · Firestore
├─ domain/usecase           # AI 로드맵 · 구간 생성 · 다음 구간 추천 · 진행률 시뮬레이션 (Firebase와 분리된 순수 Kotlin)
└─ util/
```

## 개발 단계 (1차 범위)

하단 탭: 홈 · 목표 · 기록 · 마이

| 단계 | 내용 |
|---|---|
| 0 · 1 | 프로젝트 구조 · 테마 · 로그인 · 회원가입 (완료) |
| R1 | 구간 데이터 모델 · AI 엔진(로드맵 · 구간 생성, 다음 구간 추천) · 진행률 · 샘플 데이터 (완료) |
| R2 | AI 로드맵 · 1구간 화면: 항목 수정 · 승인 → 홈, 재생성은 2차 (완료) |
| R3 | 목표 탭 · 목표 상세, 계획 탭 제거 (완료) |
| R4 | 홈: 산(체크포인트 = 로드맵 단계) + 이번 구간 (완료) |
| R5 | 구간 결과 기록 · AI 다음 구간 추천 · 승인 (완료) |
| R6 | 기록: 날짜별 · 구간별, 페이스 요약 (완료) |
| R7 | 마이 · 정상 도착 · 날짜 기반 코드 정리 (완료) |

2차(고도화): 목표 다시 맞추기, 연속 미완료 경고, 페이스 분석 상세, 회고 태그, 등반 분석 · 오른 산 모음, 알림 · 설정

---

## 팀 협업 규칙

### 브랜치
- `main` : 항상 빌드·실행되는 통합 코드. **직접 Push 금지**, Pull Request로만 병합
- `feature/<기능>` : 기능 개발 (예: `feature/auth`, `feature/home`, `feature/round-coaching`)
- 작업 시작 전 항상 `main`을 최신으로 받은 뒤 브랜치를 만들기

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
| `res/navigation/nav_graph.xml` | 기능이 커지면 기능별 `<include>` 그래프로 분리 |
| `values/strings.xml`, `colors.xml` | 화면별 접두어 사용 (`home_…`, `study_…`, `round_…`, `records_…`). 공용 색은 STEP 1 이후 추가하지 않고 기존 색 재사용 |
| `MainActivity.kt` | 화면 로직을 넣지 않기 (Fragment · ViewModel에만 작성) |
