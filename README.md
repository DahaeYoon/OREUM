# 오름 (OREUM)

> 나의 목표를 향해 한 걸음씩, 함께 정상까지

AI 기반 개인 적응형 학습 계획 및 실행 코칭 앱
사용자가 학습 목표를 설정하면 AI가 개인의 학습 수준과 상황을 고려하여 맞춤형 학습 계획을 생성하고, 실제 학습 결과에 따라 다음 계획을 지속적으로 조정하는 적응형 학습 코칭 앱


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
## 개발 기술 및 구현 방식
-개발 기술
•	개발 환경: Android Studio
•	개발 언어: Kotlin
•	UI: XML, ViewBinding
•	사용자 인증: Firebase Authentication
•	데이터베이스: Firestore
•	AI: 생성형 AI API
•	학습 데이터 계산: Kotlin
•	협업 및 버전 관리: Git, GitHub

-생성형 AI 담당
•	전체 학습 로드맵 제안
•	현재 회차 상세 학습 계획 생성
•	학습 결과에 따른 다음 회차 계획 제안
•	완료 결과에 따른 학습 범위 및 난이도 조정
•	미완료 원인별 복구 방법 제안
•	학습 기록 기반 개인화 피드백 생성

 -Kotlin 알고리즘 담당
•	목표 마감일까지 남은 날짜 계산
•	학습 가능 시간과 예상시간 비교
•	회차별 실제 학습시간 기록 및 집계
•	계획 대비 실제 수행시간 비교
•	학습 항목 및 로드맵 진행률 계산
•	남은 학습 범위와 예상 소요시간 비교
•	AI 추천 계획의 시간 조건 검증
•	회차 완료 상태 및 다음 회차 전환 관리

## 개발 단계

STEP 0 프로젝트 구조 + GitHub → 1 테마·로그인 → 2 홈·산 진행률 → 3 내 학습 CRUD → 4 캘린더 → 5 위험 감지 → 6 경로 재탐색·승인 → 7 그룹 → 8 스터디 모집 → 9 가입신청서 → 10 역할 분담·그룹 알림 → 11 Firestore 권한·통합 테스트

---


### 브랜치
- `main` : 항상 빌드·실행되는 통합 코드. **직접 Push 금지**, Pull Request로만 병합
- `feature/<기능>` : 기능 개발 (예: `feature/auth`, `feature/home`, `feature/calendar`)
- 작업 시작 전 항상 `main`을 최신으로 받은 뒤 브랜치를 만들기
- 
### Commit 메시지 (Conventional Commits)
```
feat: add login screen
fix: resolve calendar crash
refactor: separate task repository
docs: update project README
chore: bump navigation version
```

### 개발 구현 우선순위
1.	로그인 및 회원가입
2.	학습 목표 설정
3.	전체 학습 로드맵 생성
4.	현재 회차 상세 계획 생성
5.	학습 결과 기록
6.	AI 다음 회차 계획 조정 및 복구
7.	개인 학습 페이스 분석
8.	화면 간 데이터 연동 및 통합 테스트
9.	성취 보상 및 캐릭터 등 선택적 확장 기능




