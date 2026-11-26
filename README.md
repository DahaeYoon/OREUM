# 오름 (OREUM)

학습 목표를 산으로 보고, 실제 학습 결과에 따라 AI가 다음 학습 구간을 제안하는 적응형 학습 코칭 Android 앱입니다.

- 핵심 흐름: 목표 설정 → AI 로드맵 · 1구간 생성 · 승인 → 구간 진행 · 결과 기록 → AI 다음 구간 추천 · 승인 → 정상 도착
- AI는 전체 로드맵(단계 · 범위, 날짜 없음)과 지금 할 구간만 만듭니다. 미래 날짜별 학습 일정은 만들지 않고, 실제로 공부한 날짜 · 시간만 기록합니다.
- AI 기능은 Firebase AI Logic(Google Gemini)으로 실제로 생성합니다. AI 요청이 실패하거나 인터넷이 안 되면 시뮬레이션으로 대신하지 않고 "다시 시도" 화면을 보여 줍니다.
- AI 계획 · 추천은 사용자가 승인하기 전에는 자동으로 적용하지 않습니다. 이미 오른 진행률은 줄지 않습니다.

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

## 사용 기술

| 분야 | 기술 |
|---|---|
| 앱 | Kotlin, XML Layout + ViewBinding, Navigation Component, MVVM + Repository |
| 로그인 | Firebase Authentication (이메일 · 비밀번호, 비밀번호 재설정) |
| 데이터 저장 | Cloud Firestore (계정별 목표 · 구간 · 학습 기록) |
| AI | Firebase AI Logic (Google Gemini), JSON 응답 형식 지정 · 앱에서 검사 · 보정 |
| 보안 | Firebase App Check (release: Play Integrity, debug: 디버그 토큰) |
| 진행률 · 시간 계산 | Kotlin (로드맵 진행률, 구간 기준 계획, 학습 시간 집계) |

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


