# 오름 (OREUM)

> 나의 목표를 향해 한 걸음씩, 함께 정상까지

AI 기반 개인 적응형 학습 계획 및 실행 코칭 앱


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

## 개발 단계

STEP 0 프로젝트 구조 + GitHub → 1 테마·로그인 → 2 홈·산 진행률 → 3 내 학습 CRUD → 4 캘린더 → 5 위험 감지 → 6 경로 재탐색·승인 → 7 그룹 → 8 스터디 모집 → 9 가입신청서 → 10 역할 분담·그룹 알림 → 11 Firestore 권한·통합 테스트

---

## 팀 협업 규칙

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

### 같은 파일 충돌 줄이기
| 파일 | 규칙 |
|---|---|
| `res/navigation/nav_graph.xml` | 기능이 커지면 기능별 `<include>` 그래프로 분리 |
| `values/strings.xml`, `colors.xml` | 화면별 접두어 사용 (`home_…`, `study_…`, `group_…`). 공용 색은 STEP 1 이후 추가하지 않고 기존 색 재사용 |
| `MainActivity.kt` | 화면 로직을 넣지 않기 (Fragment · ViewModel에만 작성) |

