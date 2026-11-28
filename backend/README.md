# 얼쑤 Backend

공개곡의 선택적 `scoreVersion` 조회와 기존 연주 결과 보존은 [판본 계약](../docs/specs/published-score-history.md)·[설계와 검증](../docs/knowledge/backend/published-score-history.md)을 따릅니다. 미지정 조회는 현재 판본이며, 구판은 V38 보관 표와 게시기의 원자적 변경이 준비돼야 조회할 수 있습니다.

약관 공개·등록 작업은 [실행 안내](../infra/terms/README.md)를 따릅니다. 버전별 HTML 및 앱 외 계정 삭제 안내는 `LEGAL_PUBLICATION_ENABLED=true`이고 국내 LocalRecordingStorage가 연결된 뒤에만 공개됩니다. 기본값은 false입니다. 운영 저장·보유기간·최종 본문을 확인하지 않은 상태에서 활성화하거나 terms를 임의 등록하지 않습니다.

Spring Boot + Kotlin으로 구성한 독립 Gradle 애플리케이션입니다. MyBatis와 Flyway로 [ERD·정본 SQL](../docs/contracts/db-schema.md)의 MySQL 8.4 저장 계층을 구성합니다. 프로젝트명과 `spring.application.name`은 `eolssu`, 기본 패키지는 `com.ssafy.eolssu.backend`입니다. IDE에서는 저장소 루트가 아닌 `backend/`를 Gradle 프로젝트로 엽니다.

## 개발 환경

| 항목 | 버전 및 위치 |
| --- | --- |
| JDK | 21 |
| Spring Boot | 4.1.1 |
| Kotlin | 2.3.21 |
| Gradle | Wrapper 9.8.2 |
| MyBatis Spring Boot Starter | 4.0.0, Spring Boot 4 호환 |
| DB | MySQL 8.4, Flyway·Connector/J 버전은 Spring Boot 관리 |
| 플러그인·라이브러리 관리 | `gradle/libs.versions.toml` |

[Spring Initializr](https://start.spring.io/)에서 Gradle Kotlin DSL, Kotlin, Java 21, Spring Web, Validation을 선택한 생성본을 기반으로 구성했습니다. Kotlin JVM·Spring 플러그인과 JSON 변환 의존성을 사용하며, Spring Boot가 관리하는 라이브러리 버전은 중복 지정하지 않습니다. 프론트엔드와 Gradle 모듈·버전을 공유하지 않습니다.

JDK 21을 설치하고 `JAVA_HOME`이 설치 경로를 가리키도록 설정합니다. Kotlin이나 Gradle을 별도로 전역 설치할 필요는 없습니다. 첫 실행에는 Wrapper와 의존성을 내려받을 네트워크 연결이 필요합니다.

## 실행과 검증

아래 명령은 `backend/`에서 실행합니다. PowerShell에서는 `JAVA_HOME`을 설정한 터미널을 사용합니다.

| 목적 | PowerShell | macOS / Linux |
| --- | --- | --- |
| 개발 서버 | `.\gradlew.bat bootRun` | `./gradlew bootRun` |
| 테스트 | `.\gradlew.bat test` | `./gradlew test` |
| 전체 빌드·테스트 | `.\gradlew.bat clean build` | `./gradlew clean build` |
| 실제 MySQL 통합 검사 | `.\gradlew.bat databaseTest` | `./gradlew databaseTest` |
| 실제 녹음 파일 검사(ffmpeg) | `.\gradlew.bat mediaTest` | `./gradlew mediaTest` |
| 실행 JAR 생성 | `.\gradlew.bat bootJar` | `./gradlew bootJar` |

기본 주소는 `http://localhost:8080`입니다. `/` 요청의 `404`는 정상입니다. 약관 조회는 아래의 `db` 프로필에서 제공합니다. 시작 로그에서 애플리케이션과 내장 Tomcat의 기동을 확인하며, 개발 서버는 `Ctrl+C`로 종료합니다.

실행 JAR은 `build/libs/eolssu-0.0.1-SNAPSHOT.jar`입니다. `java -jar build/libs/eolssu-0.0.1-SNAPSHOT.jar`로 실행합니다. `*-plain.jar`는 실행용 Spring Boot JAR이 아닙니다.

기본 필수 검사는 `clean build`입니다. 시작 테스트는 외부 DB·AI 서버 없이 임의 포트에 실제 HTTP 서버를 띄우고, DB 관련 Bean 미생성과 미등록 경로의 `404` 응답을 확인합니다. 결과는 `build/reports/tests/test/index.html`에 생성됩니다. 저장 모델·쿼리·마이그레이션 변경에서는 아래의 `databaseTest`, 녹음 파일 검사 변경에서는 `mediaTest`도 병합 전에 수행합니다. AI 통합과 모델 품질은 두 검사 범위에 포함하지 않습니다.

마이그레이션 파일은 `V숫자__소문자_설명.sql`이고 버전은 1부터 빈 번호 없이 하나씩 씁니다. `MigrationFileTests`가 일반 `test`에서 버전 중복·공백·이름을 검사합니다. 다른 브랜치가 같은 번호를 먼저 병합하면 이 검사가 배포를 막습니다. 병합 직전 develop을 받아 나중 쪽 번호를 최신 다음으로 옮기고, 운영에 적용된 파일은 고치지 않습니다.

### 내 악보(SHT-01·02·04·08·11)

기존4/4 오선보가 굿거리 기본값으로 저장된 S15P31E102-880 복구는 [복구 조건](../docs/specs/staff-four-beat-import.md)에 한정합니다. `python tools/prepare_staff_four_beat_repair.py SNAPSHOT.json CATALOG.json PREPARED.json --user-id 6 --sheet-id 32 --expected-revision 6`은 검토할 초안과 버전·해시 조건을 준비하며 DB에 쓰지 않습니다. 계정·악보·revision은 실제 대상에서 확인한 값으로 지정하고, 출력은 원본과 함께 Git에서 제외된 비공개 위치에 둡니다. 이미 적용했거나 빠르기·구간 계획·원본·카탈로그가 예상과 다르면 거부합니다. `python -m unittest discover -s tools/tests -p test_prepare_staff_four_beat_repair.py -v`로 보존·거부 조건을 확인합니다.

S15P31E102-390·413은 내 악보 목록·조회·확정 저장·초안·삭제를, 471·472는 PDF·PNG·JPEG 불러오기와 단일 인식 작업자를 구현합니다(V16·V20·V26·V27). 534의 [원본 비보관 정책](../docs/specs/user-sheets.md#업로드-원본-비보관-정책-2026-11-06-확정-s15p31e102-534)에서 업로드 원본은 R2 대신 backend의 비공개 임시 디렉터리에만 둡니다. 성공·실패·삭제 시 즉시 지우고 최대 30분 뒤에도 지웁니다. 시작 시 남은 임시 파일을 정리하고 QUEUED·RUNNING을 FAILED(SERVER_ERROR)로 끝냅니다. 원본 쪽 미리보기는 없고 `source.pages=[]`, `pagesExpireAt=null`, `retryable=false`입니다. 기존 재시도 경로는 본인 변환에도 409이며, 실패 후 파일을 새 Idempotency-Key로 다시 올립니다. 기존 R2 원본의 일괄 삭제는 이 변경에서 실행하지 않습니다.

인식 연결에는 `SHEET_CONVERSION_OMR_BASE_URL`(Compose 기본 `http://omr:8000`)을 사용하며 호출 제한은 630초입니다. OMR는 `SHEET_IMPORT_READ_BASE_URL`(기본 `http://backend:8080/api/internal/sheet-imports`)의 만료 토큰 URL로 원본을 읽습니다. 외부 Nginx는 `/api/internal/`을 404로 차단하며 이 경로의 접근 로그도 끕니다. 한 backend 인스턴스에서 실행하고, 임시 디렉터리를 영구 볼륨이나 백업 대상에 넣지 않습니다. `eolssu.scheduling.enabled=false`는 큐·시작 복구·주기 정리를 끄는 테스트 전용 설정이므로 운영에서는 쓰지 않습니다. 원본 불러오기는 R2 연결값이 없어도 동작하며, `SHEET_STORAGE_*`는 아래 생성 음원과 사용자가 삭제하는 과거 원본용입니다.

S15P31E102-684에서 내 악보 음원을 임시 다운로드로 변경했습니다. `POST /api/users/me/sheets/{sheetId}/audio`로 M4A 생성을 접수하고, 같은 경로의 GET으로 상태와 다운로드 링크를 조회합니다. 완료된 M4A는 `sheet-audio/`에 10분간 두고 만료된 객체는 작업자가 정리합니다. V37은 완료 파일의 기한을 저장합니다. `SHEET_AUDIO_BASE_URL`이 비었거나 저장소가 없으면 새 요청은 503입니다. 작업자는 `eolssu.scheduling.enabled=false`에서 돌지 않습니다. 실제 생성기 경로까지 검사하려면 `databaseTest`에 `PLAYBACK_TEST_BASE_URL`을 지정합니다. [내 악보 음원 스펙](../docs/specs/sheet-audio.md)

### 카탈로그 조회(CAT-03~10)

S15P31E102-491의 범위는 CAT-08 미리듣기 음원 API와 CAT-04 반주 응답입니다. CAT-08의 `soundUrl`은 `/api/jangdans/{jangdanId}/audio`를 가리키며 이 경로는 160이 게시한 비공개 R2 M4A의 15분 서명 URL로 이동합니다. 기존 모범 음원과 동일한 `RECORDING_STORAGE_*` 설정을 사용합니다. CAT-04의 선택적 `jangdanPlayback`은 현재 악보의 장단명과 틱 구조, 카탈로그 구음에서 시작점과 한 주기를 계산합니다. 시작점 또는 주기를 정할 수 없으면 반주만 null입니다. 기존 곡 FK·BPM·악보·모범 음원을 바꾸지 않는 읽기 확장이라 새 DB 마이그레이션은 없습니다. [API 계약·악보 구조 판정·서버 검증](../docs/knowledge/backend/jangdan-practice-links.md)을 따릅니다.

통합 순서는 DATA160(MR !324) → BE491 → FE340(MR !319)입니다. 백엔드 실행·검사는 160의 검수 목록·음원 자산을 먼저 반영한 상태에서 수행합니다. 앱의 화면·재생 엔진·응답 소비와 앱 검증은 S15P31E102-340의 FE MR 범위입니다. 전수 대조는 공개 CAT-04 응답을 `{songId}.json`으로 저장한 디렉터리를 `JANGDAN_SNAPSHOT_DIR`에 지정하고 `.\gradlew.bat test --tests '*JangdanPlaybackSnapshotTests' --rerun-tasks`로 실행합니다. 각 파일의 `result.score`를 운영 검수 manifest와 대조하며 네트워크나 DB는 사용하지 않습니다. 디렉터리를 지정하지 않으면 이 선택 검사만 생략되고, 기본 단위 검사는 작은 악보를, DB 검사는 대표 원본 한 곡을 사용합니다. `--rerun-tasks`는 환경변수 변경 전 결과를 재사용하지 않도록 합니다.

S15P31E102-409의 V18은 [내장 운지 계약](../docs/specs/builtin-fingering-catalog.md)에 따라 92개 참고 운지를 연결합니다. V17의 악기·율명 뒤에 적용하며, `fingering_charts.pattern_json`과 nullable `image_url`이 필요합니다. 기존 이미지·설명·ID는 보존합니다. 새 백엔드의 `ops` 배포 전에 V18 적용이 필요하며 이번 작업은 로컬 검증 범위입니다. 이미지가 필수인 구버전으로 되돌릴 때는 새 null 행을 읽을 수 있도록 먼저 호환성을 준비해야 합니다. `BuiltinFingeringApiDatabaseTests`는 FE 원본 전체와 실제 HTTP·pitchId 필터를 대조합니다. [검증 기록](../docs/knowledge/backend/builtin-fingering-catalog.md).

S15P31E102-408: `GET /api/songs`는 선택적인 `instrumentId`를 받습니다. 생략하면 기존 전체 목록, 지정하면 활성 악기의 게시·미삭제 곡만 ID 순서로 반환합니다. 잘못된 ID는400, 없는/비활성 악기는404, 곡이 없는 활성 악기는200 빈 목록입니다. [곡 목록 악기 필터](../docs/specs/song-instrument-filter.md)의 FE 선택과 연결되며 검증은 `SongControllerTests`와 실제 MySQL·HTTP의 `SongInstrumentApiDatabaseTests`를 사용합니다.

S15P31E102-411: V21의 자진모리·중중모리·엇모리·단모리·타령·계면가락도드리·세령산으로 장단 카탈로그10종을 제공합니다. 기존 ID·곡·음원은 보존합니다. 정악3종의 시작 BPM48·48·63(QUARTER)은 사용자 승인 서비스값이며 원전 확정 빠르기가 아닙니다. [근거·검증](../docs/knowledge/backend/jangdan-catalog-extension.md)을 확인합니다. 운영 기동의 Flyway는 꺼져 있고, 실제 배포 시 [배포 마이그레이션 단계](../docs/knowledge/infra/ops-flyway-auto-migration.md)가 별도로 적용합니다. 이번 작업은 로컬 검증이며 배포는 실행하지 않습니다.

`db`·`ops`에서 로그인 없이 악기·악보·모범 음원·율명·운지·장단·기호를 조회합니다. 경로/필터/빈 데이터/초안과 현행 계약의 차이는 [카탈로그 API](../docs/specs/catalog-read-apis.md)에 있습니다. 모범 음원은 V8 `song_model_audios`의 게시 행이 현재 곡 악보 버전·BPM/단위와 같을 때만 반환합니다. V8은 운영 음원을 자동 적재하지 않습니다. `ops` 기동 전에 배포 담당자가 V8을 별도로 적용해야 합니다. 관련 `test`와 폐기 가능한 MySQL의 `databaseTest`를 수행합니다.

S15P31E102-407의 V17은 단소·소금·정악대금·산조대금을 공개하고 FE 운지에 필요한 누락 율명을 보충합니다. 기존 악기/pitch ID·한자·반음은 보존합니다. 새 DB 기준 전역36음, 단소26·소금24·두 대금 각34음입니다. 이는 등록된 명목상 음역의 조회이며 모든 음의 운지·녹음·채점 승인을 뜻하지 않습니다. 회원 악기 선택도 두 대금을 나누어4종이며 명시적 서버 code로 ID를 찾습니다. [범위와 계약](../docs/specs/four-instrument-catalog.md), [검증](../docs/knowledge/backend/four-instrument-catalog.md)을 따릅니다. 사용자 후속 요청으로2026-11-03 운영 V17을 적용했습니다([운영 기록](../docs/knowledge/infra/four-instrument-ops-migration.md)). 앱 코드 배포와 구분하며 새 환경은 해당 마이그레이션이 필요합니다.

### 한 음씩 연습 판정 기준(PRA-06)

S15P31E102-493: `db`·`ops`에서 로그인 없이 `GET /api/note-practice/criteria?instrumentId=`로 악기별 판정 기준을 조회합니다. 기준은 `application.yml`의 `eolssu.note-practice.criteria`에 악기 code별로 있으며 테이블·마이그레이션은 없습니다. 값은 AI 미확정 초안이고, 바꾸면 백엔드 재배포·재시작만 필요하며 앱 배포는 필요 없습니다. 잘못된 값은 서버 시작을 막습니다. 계약과 초안 값은 [스펙](../docs/specs/note-practice-criteria.md)입니다. 검증은 `NotePracticeCriteriaControllerTests`, `NotePracticePropertiesTests`, 실제 MySQL의 `NotePracticeCriteriaApiDatabaseTests`입니다.

### 정간보 기호 사전(CAT-09)

[410 계약](../docs/specs/notation-symbol-catalog.md)의 V19은 기존 FE 시김새 그림47개와 쉼표·연장2개를 등록하며, 같은 symbol의 기존 행은 보존합니다. DB 행의 이름·설명·이미지 상태가 번들 내용과 일치할 때만 출처·검수 상태와 `/api/notation-symbols/{symbolId}/image`를 제공합니다. 이미지는 MIT 고지를 포함한 SVG이며 공개 GET만 허용합니다. 노라느니는 추정 설명입니다. 운영은 V18 다음 V19 적용과 새 서버 배포를 별도로 준비해야 하며, 이 작업에서 운영 적용은 하지 않았습니다.

사전 원본 대조는 저장소 루트에서 `python backend/tools/sync_notation_symbols.py --check`로 실행합니다. FE VectorDrawable의 path·group과 설명을 그대로 SVG·사전으로 옮겼는지 확인합니다. 생성 스크립트는 V19 배포 전 스냅샷용이며, 배포 후 변경에는 새 마이그레이션을 추가해야 합니다. 실제 DB/HTTP·이미지·기존 행 보존 검사는 `CatalogReferenceApiDatabaseTests`에 있습니다.

### 기본음·시김새 음원 카탈로그

S15P31E102-364: V13 `reference_audio_assets`는 네 악기의 라벨별 M4A와 출처·추정 상태를 연결합니다. 같은 V13에 기본음 자산의 명목상 기준 음을 가리키는 nullable `pitch_id` FK와 근거를 포함합니다. 서비스 지원 음역·실측 검수·채점 기준과 구분하며 사용자 녹음 저장소는 그대로입니다. 마이그레이션만으로 자산을 적재하거나 연결하지 않습니다. 운영 `ops` 배포 전 최종 V13 하나를 별도 적용해야 합니다. [명시적 매핑 절차](../infra/catalog/README.md#명목상-기준-음-연결)를 따릅니다. 운영은 V12까지 적용됐고 새 카탈로그 두 테이블이 없음을2026-11-02 읽기 전용으로 확인하여 미적용 개발 파일V14~V16은 V13에 통합했습니다. 기존 로컬 검증DB는 repair하지 않고 새 폐기DB를 사용합니다.

현재 통합 백엔드는 학습 기능의 V14도 필요합니다. 배포 시 오디오 V13 다음 학습 V14를 적용하고, 학습 사전 확인과 운영 결과는 [368 게시 기록](../docs/knowledge/infra/reference-audio-publication.md)을 따릅니다.

V13은 라벨에 대응하는 누락 기준 음11행을 추가합니다. 기존11개 ID/반음과 단소 악기의 범위는 보존하며 전역 `GET /api/pitches`는22개, 기존 범위 방식의 단소 조회는15개를 제공합니다(사용자 선택). V13 적용 뒤 단소30·정악54·소금90·산조45의 명시적 매핑을 실행하면 기본음219개가 연결됩니다. 같은 V13의 역할별 관계에 시김새207개/315관계를 추가하면 총426자산이 연결되고 개별 라벨 대안4개만 미연결입니다. `data/reference/pitches.json`의 단소 전사 기준이나 운지표를 자동 확대하지 않습니다.

- `GET /api/reference-audios?datasetId=danso`: datasetId 필수(`danso`, `sogeum`, `jeongak-daegeum`, `sanjo-daegeum`). `category`, `technique`, `register`, `yulmyeong`, `pitchId`, `relatedPitchId`를 조합합니다. pitchId는 선행 0 없는 1~9223372036854775807 십진 문자열이고 그 외400입니다. 게시 행만 labelId 순서로 반환합니다. 없는 조합은 빈 목록, 잘못된 datasetId·빈 필터는400입니다. 목록·상세 자산의 `pitchId`는 기본음 전용 문자열 또는 명시적 null입니다. `relatedPitchId`는 같은 ID 형식이며 기본음 FK 또는 역할별 관계 중 일치하는 음이 있는 자산을 중복 없이 찾습니다. 모든 필터는 AND입니다. 목록·상세의 `pitchRelations` 배열은 `{role,sequenceIndex,pitchId,evidencePath}`를 반환합니다. LABEL, SEQUENCE(원본순서), LOWER_ANCHOR, DESTINATION 순이며 SEQUENCE 외 index는 null입니다. evidencePath는 상세 metadata를 기준으로 한 JSON Pointer이고 기본음은 빈 배열입니다.
- `GET /api/reference-audios/{assetId}`: `result.asset`에 음원 정보, `result.metadata`에 원본 라벨·출처·변환 근거를 반환합니다. 미등록·초안은404입니다.
- `GET /api/reference-audios/{assetId}/play`: 게시된 DB 키에만900초 서명 GET URL을 만들어302로 이동합니다. `Location`과 `Cache-Control: no-store`, 본문 없음. 저장소 미설정은 공통503입니다. 목록·상세의 `soundUrl`은 `/api/reference-audios/{assetId}/play` 형식이며 API 원점에 대해 해석하는 상대 경로입니다.

읽기만 공개하며 만료 로그인 쿠키에 영향받지 않습니다. 업로드·등록·게시 HTTP API는 없습니다. 출처 검토와 실제 객체의 해시·재생을 확인한 뒤 운영 적재 담당자가 `DRAFT`를 `PUBLISHED`로 바꿉니다. 데이터셋/라벨 ID를 임의 대표 율명으로 축약하지 않고, `inferred`를 `reviewed`나 채점 정답으로 승격하지 않습니다.

`REFERENCE_AUDIO_STORAGE_ENDPOINT`, `BUCKET`, `ACCESS_KEY_ID`, `SECRET_ACCESS_KEY`, `REGION`은 각각 `REFERENCE_AUDIO_STORAGE_` 접두사를 사용합니다(`.env.example`). HTTPS S3 API 엔드포인트·버킷·읽기 권한 키를 주입합니다. 설정 누락은 기동을 막지 않고 재생만503으로 답합니다. DB에 저장하는 것은 `reference-audio/v1/{datasetId}/{assetId}/{sha256}.m4a` 키이며 만료형 URL이 아닙니다. R2 버킷의 공개 정책이나 녹음 삭제 규칙은 변경하지 않습니다. 검증은 `ReferenceAudioControllerTests`, `ReferenceAudioSignerTests`, 실제 MySQL의 `ReferenceAudioApiDatabaseTests`와 전체 `clean build`/`databaseTest`를 사용합니다.

### 약관 목록(AUTH-08)

`db` 프로필과 V2 이상 스키마에서 `GET /api/terms`를 로그인 없이 호출합니다(`Accept: application/json`). 응답은 `{httpStatus,isSuccess,message,code,result:{items:[...]}}`이며 항목은 문자열 `termId`, `code`, `title`, `required`, `version`, `contentUrl`입니다. UTC 조회 시각까지 시행된 버전 중 code별 가장 늦은 `effective_at`을 고르고, 같은 시각이면 큰 ID 한 개를 반환합니다. `seq`·ID 오름차순으로 표시하며 현재 약관이 없으면 200과 빈 목록입니다. 문구·URL·초기 데이터는 자동 생성하지 않습니다.

무DB 기본 프로필에는 약관 컨트롤러·조회 Bean을 만들지 않습니다. 업무 Bean은 `@Profile("db | ops")`라 운영 `ops`에서도 만듭니다. `databaseTest`의 `TermsApiDatabaseTests`는 폐기 가능한 MySQL 8.4에 고정 약관 데이터를 넣고 실제 HTTP 응답까지 확인한 뒤 약관 데이터를 지웁니다.

400(JSON·검증·파라미터 오류), 405(메서드 오류, `Allow` 보존), 500(예상하지 못한 예외)은 기존 공통 오류 본문을 사용하며 요청값·예외 메시지는 응답에 넣지 않습니다. ProblemDetail은 쓰지 않습니다(2026-10-28 확정, S15P31E102-168 녹음 업로드 오류도 같은 형식). 위 목록에 없는 프레임워크 상태(예: 409·410)도 같은 형식이며 `result.errorCode`는 HTTP 상태 이름입니다.

## 환경 설정

공통 설정은 `src/main/resources/application.yml`, 마이그레이션을 포함하는 DB 설정은 `application-db.yml`에 둡니다. 기본 실행은 DB 없이 시작하며, `SERVER_PORT` 기본값은 `8080`입니다. 운영은 `ops` 프로필을 사용합니다. `ops`는 `db`와 같은 업무 API·Mapper를 켜되 Flyway를 끄고 기존 DB를 그대로 씁니다. 운영 DB의 마이그레이션은 Jenkins 배포 도구가 컨테이너 교체 직전에 같은 JAR을 `db` 프로필 일회성 컨테이너로 실행해 적용합니다. 데이터를 지우거나 이름을 바꾸는 변경은 사람의 승인이 있어야 적용합니다([운영 구성](../infra/runtime/README.md)의 운영 DB 마이그레이션).

### 이메일 인증(AUTH-06·07)

`db` 프로필은 `AUTH_CODE_HMAC_SECRET`이 필수입니다. 임의 32바이트 이상을 Base64로 인코딩한 값을 한 번 발급해 비밀값 저장소에 보관하고 실행 환경에 주입합니다. 비어 있거나 형식·길이가 잘못되면 시작에 실패합니다. 키를 바꾸면 기존 인증번호 확인이 실패하므로 매 실행 때 새로 만들지 않습니다. 테스트는 `src/test/resources/application.properties`의 공개 고정 키를 쓰며 실행 JAR에는 포함하지 않습니다.

`AUTH_MAIL_MODE` 기본값은 `smtp`입니다. `AUTH_MAIL_HOST`·`AUTH_MAIL_USERNAME`·`AUTH_MAIL_PASSWORD`가 모두 있으면 STARTTLS(설정 고정)로 실제 발송하고, 하나라도 없으면 서버는 기동하되 발송만 거절합니다. 발송 거절·실패 때 가입 인증 발송은 503이며, 비밀번호 재설정·계정 연결은 가입 여부를 숨기기 위해 202를 반환합니다. SMTP 시간 제한은 연결 3초·읽기/쓰기 5초입니다. `unavailable`은 항상 발송을 거절합니다. 로컬에서는 `AUTH_MAIL_MODE=memory`로 메모리 대역을 명시할 수 있습니다. 최근 100건을 프로세스 안에서만 보관하고 로그·HTTP 조회·파일 저장을 제공하지 않습니다. 번호 확인은 IDE에서 `InMemoryVerificationMailSender.messages()`로 할 수 있습니다.

마이페이지 문의하기(`POST /api/users/me/inquiries`)는 같은 `AUTH_MAIL_MODE`·`AUTH_MAIL_*` 계정으로 `INQUIRY_MAIL_RECIPIENTS`(쉼표 구분, 기본값은 `application.yml`의 개발자 주소 3개)에 보냅니다. SMTP 계정이나 받는 주소가 비면 서버는 기동하고 문의만 503입니다. 형식이 틀린 주소는 기동을 막습니다. `memory`는 보내지 않고 성공 처리합니다. 문의 내용은 DB에 저장하지 않으며, 회원마다 60분에 5번까지 서버 메모리에서 셉니다.

- `POST /api/auth/email-verifications`: JSON `email`(최대 100자·소문자 정규화, S15P31E102-1014)과 `purpose`(`SIGN_UP`, `PASSWORD_RESET`, `ACCOUNT_MERGE`). 202에 문자열 `verificationId`, UTC `expiresAt`, `resendAvailableAt`을 반환합니다. 같은 이메일의 모든 목적을 합산해 60초 간격·KST 날짜별 10회이며 재발송하면 같은 목적의 이전 번호·미사용 완료 토큰을 무효화합니다.
- `POST /api/auth/email-verifications/{verificationId}/confirm`: JSON `code`는 숫자 6자리 **문자열**입니다. 3분 경과·재발송·확인 성공 뒤에는 410, 없는 ID는 404입니다. 처음 네 오답은 400과 `result.details.remainingAttempts`, 다섯 번째 오답부터는 429입니다. 네 번 틀린 뒤 다섯 번째에 맞히면 성공합니다.
- 확인 성공 시 임의 256비트 `verificationToken`, 정규화 이메일, 성공 시각부터 30분 뒤의 `expiresAt`을 반환합니다. DB에는 번호 HMAC-SHA256과 완료 토큰 SHA-256만 저장합니다. 가입(AUTH-02)이 완료 토큰을 1회 소비합니다. 계정 연결·비밀번호 재설정의 소비는 후속 Task(S15P31E102-211·210)입니다.
- 활성·탈퇴 대기 회원의 SIGN_UP은 409 `EMAIL_ALREADY_EXISTS`입니다. PASSWORD_RESET은 미가입자에게도 동일한 202와 제한을 적용하고 실제 메일은 가입된 이메일에만 보냅니다. 미가입·메일 장애 요청에는 6자리 코드로 확인할 수 없는 해시를 저장합니다. 재설정 메일 장애는 개인정보 없는 경고 로그만 남기며 응답에 드러내지 않습니다.
- 발송 간격·일일 제한의 429는 `result.details.resendAvailableAt`을 제공합니다. 가입 메일 장애 503은 신규 행·이전 번호 무효화를 롤백합니다. 재설정·계정 연결의 숨긴 발송 실패는 요청 행·횟수·이전 번호 무효화를 유지합니다. 서버 안에서 DB 연결을 잡기 전에 먼저 줄을 세운 뒤 단일 MySQL의 DB별 잠금으로 최초 동시 발송과 번호 재확인을 직렬화하며, 5초 내 차례를 얻지 못하면 공통 503입니다([선택과 제약](../docs/knowledge/backend/auth-token-storage.md#이메일-인증-구현의-동시성2026-10-28)).

### 가입·로그인·재발급·로그아웃(AUTH-01~04)

`db` 프로필에서 제공합니다. 로그인 토큰은 응답 본문에 넣지 않고 쿠키로만 내려갑니다.

- 쿠키: 모두 HttpOnly·SameSite=Lax이고 Secure는 `AUTH_COOKIE_SECURE`(기본 `true`, 로컬 HTTP 개발에서만 `false`)를 따릅니다. `access_token`은 HS256 JWT(`sub`=users.id, 30분, Path `/api`), `refresh_token`은 임의 256비트(14일, Path `/api/auth`, DB에는 SHA-256만)입니다.
- `POST /api/auth/sign-up`: 이메일·비밀번호(영문·숫자·특수문자 포함 8~64자, Argon2id 저장. 로그인·현재 비밀번호는 예전 규칙으로 정한 계정을 위해 더 긴 값도 받는다)·닉네임(공백이 아닌 10자 이하)·SIGN_UP `verificationToken`·`agreements`(시행 중인 필수 약관 전부 현재 버전 동의)·선택 `instrumentId`(활성 악기, 생략하면 코드 `DANSO` 악기)·`notationMode`(기본 HANJA). 201과 로그인 쿠키. 토큰 문제는 401 `EMAIL_NOT_VERIFIED`, 중복은 409, 약관은 422 `TERMS_AGREEMENT_REQUIRED`이며 어느 단계가 실패해도 회원이 남지 않습니다.
- `POST /api/auth/sign-in`: 이메일 없음·비밀번호 불일치는 같은 401 `INVALID_CREDENTIALS`입니다. 같은 이메일 15분 안 5번째 실패부터 15분 동안 429이고 `result.details.retryAvailableAt`을 줍니다. 잠금은 `users.email` 콜레이션(`utf8mb4_0900_ai_ci`)의 정렬 키 기준이라 악센트·대소문자 변형으로 피할 수 없고, 가입하지 않은 이메일에도 똑같이 걸려 가입 여부를 드러내지 않습니다. 실패 기록은 서버 메모리라 재시작하면 초기화됩니다. 비밀번호 확인(Argon2)은 DB 트랜잭션 밖에서 합니다. 탈퇴 대기 회원은 요청 후 30일 미만일 때만 로그인으로 ACTIVE로 돌아가고 `withdrawalCancelled=true`입니다. 회원 잠금을 얻은 뒤의 시각이 만료 정각 이상이면 401 `INVALID_CREDENTIALS`로 거절합니다.
- `POST /api/auth/refresh`: `refresh_token` 쿠키만 봅니다. 성공하면 이전 토큰을 폐기(ROTATED)하고 두 쿠키를 새로 줍니다. 재발급으로 폐기된(ROTATED) 토큰이 다시 오면 그 회원의 토큰을 모두 폐기(REUSE_DETECTED)합니다. 비밀번호 변경·로그아웃·탈퇴로 폐기된 토큰은 401만 줍니다. 401이면 두 쿠키를 지웁니다. 두 탭이 동시에 재발급하면 한쪽이 재사용으로 판정돼 로그아웃될 수 있습니다.
- `POST /api/auth/sign-out`: 로그인 필요. 쿠키의 refresh_token이 그 회원의 것일 때만 폐기(SIGN_OUT)하고 두 쿠키를 지웁니다.
- `AUTH_JWT_SECRET`이 없으면 서버는 기동하고 가입·로그인·재발급만 503입니다. 가입 기본 악기(`DANSO`)가 없어도 503입니다.

### 내 정보와 비밀번호 변경(USR-01~04)

`db`·`ops` 프로필에서 제공하며 모두 로그인(access_token 쿠키)이 필요합니다. 토큰은 유효하지만 회원이 없거나 ACTIVE가 아니면 401 `UNAUTHENTICATED`입니다(회원 행이 없을 때 401은 GRO-04와 같음).

- `GET /api/users/me`: 본인 이메일·닉네임·악기·표기와 연결된 간편 로그인(`linkedProviders`, 연결 순). 비밀번호 해시와 간편 로그인 제공자의 사용자 ID·이메일은 내보내지 않습니다. 닉네임·악기·표기 저장 응답에는 이메일을 넣지 않습니다.
- `PATCH /api/users/me`: 닉네임(가입과 같은 규칙, 10자 이하).
- `PATCH /api/users/me/preferences`: `instrumentId`·`notationMode` 중 한 개 이상. 없거나 비활성인 악기는 422 `UNPROCESSABLE_INPUT`. 곡별 BPM(`user_song_settings`)은 형식이 정해지지 않아 아직 받지 않습니다.
- `PATCH /api/users/me/password`: 현재 비밀번호가 틀리면 403 `CURRENT_PASSWORD_MISMATCH`, 같은 회원 15분 안 5번째 실패부터 15분 동안 429(`retryAvailableAt`, 로그인 잠금과 따로 셈). 새 비밀번호는 가입 규칙을 따르고 현재 값과 같으면 400입니다. 성공하면 그 회원의 refresh를 모두 폐기(PASSWORD_CHANGED)하고 현재 기기에만 두 쿠키를 새로 줍니다. 다른 기기의 access_token은 만료(최대 30분)까지 쓸 수 있습니다.

### 회원 탈퇴와 일괄 삭제(USR-05)

- `DELETE /api/users/me`: JSON `currentPassword`로 다시 확인합니다. 틀리면 403 `CURRENT_PASSWORD_MISMATCH`이고, 15분 5회 실패 잠금(429)은 비밀번호 변경과 함께 셉니다. 성공하면 `WITHDRAWAL_PENDING`으로 바꾸고 요청+30일 뒤 첫 04:00(Asia/Seoul)을 `purgeAt`으로 돌려주며, refresh를 모두 폐기(WITHDRAWN)하고 두 쿠키를 지웁니다. 탈퇴 취소는 요청 후 30일 미만의 로그인에만 허용하며, `purgeAt` 이전이라도 유예가 만료되면 취소할 수 없습니다.
- 인증 필터: `db`·`ops` 프로필에서 로그인이 필요한 요청마다 토큰의 회원 상태를 조회해 ACTIVE가 아니면 401 `UNAUTHENTICATED`와 두 쿠키 삭제로 응답합니다. 공개 API는 그대로입니다.
- 일괄 삭제: 매일 04:00(Asia/Seoul)에 삭제 예정 시각이 지난 탈퇴 대기 회원을 100명씩 읽어 회원 단위로 처리합니다. 녹음 삭제 전에 회원 행을 잠그고 대기 상태·삭제 예정 시각을 재확인합니다. 먼저 시작된 취소가 커밋되면 파일과 DB를 모두 보존합니다. 확인 후 잠금을 해제하고 녹음 원본을 지웁니다. 이어 [DB 스키마 계약](../docs/contracts/db-schema.md#탈퇴-일괄-삭제-대상) 순서로 한 트랜잭션에서 관련 행을 지우고 개인정보를 비워 `DELETED`로 바꿉니다. 녹음 삭제가 실패한 회원은 다음 날로 미룹니다(녹음 저장소가 설정되지 않은 서버도 같음). 끝나면 처리·미룸·건너뜀 수와 걸린 시간을 로그로 남깁니다. `eolssu.scheduling.enabled=false`면 돌지 않습니다.

### 준비 상태 검사

`GET /api/actuator/health/readiness`는 준비되면 HTTP 200과 `{"status":"UP"}`, 준비되지 않으면 HTTP 503을 반환합니다. 기본 개발 모드는 애플리케이션 준비 상태만 확인합니다. 운영 `ops` 프로필은 `DB_URL`·`DB_USERNAME`·`DB_PASSWORD`로 기존 MySQL에 연결해 DB health를 함께 확인합니다. DB 접속 실패 시 프로세스가 살아 있어도 readiness는 DOWN이고 `/api/actuator/health/liveness`는 UP일 수 있습니다.

운영 `ops`에서는 Flyway와 SQL 초기화를 끕니다. 업무 API는 기존 스키마를 읽고 쓰지만 스키마를 바꾸지 않습니다. 기존 DB에 마이그레이션을 실행하려고 `db` 프로필을 함께 활성화하지 않습니다(`db,ops`처럼 함께 켜면 나중 프로필의 Flyway 설정이 이겨 순서에 따라 Flyway가 켜집니다). 세션 시간대는 UTC(`SET time_zone = '+00:00'`), 연결 대기는 2초, 풀은 최대 10개입니다. `AUTH_CODE_HMAC_SECRET`이 비어 있어도 기동하고 이메일 인증번호 발송·확인만 503입니다. HTTP에는 health만 공개하고 상세·컴포넌트·JMX를 숨깁니다. `env`·`beans`·`configprops`는 404입니다.

필수 `clean build`에서 실제 HTTP 준비 응답·관리 정보 비공개와 연결할 수 없는 격리 DB 대상의 readiness 503/liveness 200·Flyway Bean 부재를 검사합니다. 운영 후보에서는 기존 DB 연결의 UP 응답도 별도로 확인합니다.

```powershell
$env:SERVER_PORT = '8081'
.\gradlew.bat bootRun
```

macOS / Linux에서는 `SERVER_PORT=8081 ./gradlew bootRun`을 사용합니다. `.env.example`은 변수 설명용이며 `.env` 파일을 자동으로 읽지 않습니다. 실제 비밀값은 터미널·IDE·배포 환경에서 주입하고 커밋하지 않습니다.

### 보안과 공통 오류 응답

Spring Security로 API 경로를 나누며 `access_token` 쿠키의 HS256 JWT를 검증합니다. Authorization 헤더·쿼리·폼·다른 쿠키는 인증 수단으로 사용하지 않습니다. 공개 GET·POST·health 경로는 `access_token` 쿠키를 검증하지 않아 만료·잘못된 쿠키가 있어도 접근할 수 있습니다. 재발급은 후속 단계에서 refresh 쿠키로 확인합니다. 로그인 토큰 발급·재발급은 S15P31E102-143 후속 단계에서 추가합니다.

`AUTH_JWT_SECRET`에는 별도 발급해 보관한 **UTF-8 원문 32바이트 이상**의 비밀키를 넣습니다(Base64 자동 디코딩 없음). 기본 키는 없습니다. 누락·공백이면 서버는 기동하지만 보호 API의 모든 토큰을 공통 401 `UNAUTHENTICATED`로 거절합니다. 명시한 키가 32바이트 미만이면 기동에 실패합니다. 서버 환경 연결은 S15P31E102-200에서 진행합니다. 서명·만료·nbf·필수 exp 및 양의 Long 범위 sub를 검증하며 시간 유예는 없습니다. 토큰·키는 로그나 오류 본문에 싣지 않습니다.

JWT `sub`는 `users.id`입니다. 컨트롤러에서 `@AuthenticationPrincipal jwt: Jwt`로 받고 `requireNotNull(jwt.subject).toLong()`으로 읽습니다. 의미상 `jwt.subject.toLong()`이며 Spring Security 7의 nullable 선언 때문에 `requireNotNull`을 씁니다. `Jwt`는 `org.springframework.security.oauth2.jwt.Jwt`, 어노테이션은 `org.springframework.security.core.annotation.AuthenticationPrincipal`입니다. 이 단계에서는 사용자 DB 상태를 재조회하지 않습니다.

컨트롤러 테스트는 `spring-security-test`의 `post("/api/...").with(jwt().jwt { it.subject("1") })`로 인증할 수 있습니다(`SecurityMockMvcRequestPostProcessors.jwt`). 이는 서명 검증을 대체하는 테스트 대역이며, `AccessTokenSecurityTests`는 실제 HS256 서명 쿠키·위조·만료도 별도로 검사합니다. 테스트 고정 키는 테스트 코드에만 둡니다.

- 공개(명세 Request Header '공개' 경로만, 와일드카드 없음): POST `/api/auth/sign-in`·`sign-up`·`refresh`·`password-reset`·`oauth/{provider}`·`email-verifications`·`email-verifications/{verificationId}/confirm`, GET `/api/terms`·`songs`·`songs/{songId}`·`songs/{songId}/score`·`songs/{songId}/model-audio`·`instruments`·`instruments/{instrumentId}/fingerings`·`pitches`·`jangdans`·`jangdans/{jangdanId}`·`notation-symbols`, `/api/actuator/**`(health만 노출). 전체 표는 `ApiAccessRulesTests`에 있습니다.
- 그 밖의 `/api/**`는 로그인이 필요합니다. 오류는 API 명세 공통 형식 `{httpStatus, isSuccess:false, message, code, result{errorCode}}`로 응답합니다(400 `INVALID_REQUEST`, 401 `UNAUTHENTICATED`, 403 `FORBIDDEN`, 404 `RESOURCE_NOT_FOUND`, 405 `METHOD_NOT_ALLOWED`, 406 `NOT_ACCEPTABLE`, 413 `RECORDING_TOO_LARGE`, 415 `UNSUPPORTED_MEDIA_TYPE`, 500 `INTERNAL_SERVER_ERROR`, 녹음 업로드 코드는 아래 '녹음 업로드와 원본 저장소'). Spring multipart 한도 초과도 같은 413 형식으로 처리합니다. 지원하지 않는 Accept의 406도 공통 JSON 본문으로 반환합니다. 오류 추가 정보는 필요할 때만 `result.details`에 담고 민감값은 제외합니다. ProblemDetail 형식은 쓰지 않기로 확정했습니다. `//api`·`/./`·`;x`처럼 비정상 경로는 Spring Security 방화벽이 400 `INVALID_REQUEST`로 거절합니다. 인코딩된 `/` 등 Tomcat이 먼저 거절하는 경로는 Tomcat 기본 응답입니다.
- CSRF: CSRF 토큰 대신 SameSite=Lax 쿠키와 `StateChangingRequestFilter`로 막습니다. 경로와 관계없이 모든 POST·PUT·PATCH·DELETE에 적용합니다.
  - `Origin` 헤더가 있으면 요청 `Host`와 호스트 이름이 같거나 `SECURITY_ALLOWED_ORIGINS`의 주소여야 합니다(403). Nginx는 HTTPS를 끝내고 `Host`를 포트 없이 넘기므로, `Host`에 포트가 없으면 호스트 이름만 비교하고 포트가 있으면 포트도 비교합니다. 그래서 Nginx 뒤에서는 같은 호스트의 다른 포트에서 온 요청도 통과합니다. 지금 서버는 Nginx의 80·443만 공개하므로 문제가 없지만, 같은 도메인의 다른 포트에 서비스를 올리면 이 판단을 다시 확인합니다. 앱처럼 `Origin`을 보내지 않는 요청은 Origin 확인을 건너뜁니다.
  - 본문은 `application/json`만 받고, `Content-Type`이 없으면 본문도 없어야 합니다(415). 녹음 업로드(`POST /api/practice-sessions/{id}/recording`, 디코딩된 경로 기준)만 `multipart/form-data`를 받습니다.
- CORS: `SECURITY_ALLOWED_ORIGINS`가 비어 있으면 켜지 않습니다. 운영·compose는 웹과 API가 같은 주소이고, Nginx 뒤 백엔드는 http로 받아 Spring CORS가 https `Origin`을 다른 출처로 잘못 판단하기 때문입니다. FE 개발 서버처럼 다른 주소에서 쿠키를 포함해 **백엔드를 직접** 부를 때만 그 주소를 쉼표로 구분해 넣습니다. `*`는 쿠키 허용 설정과 함께 쓸 수 없어 쓰지 않습니다. Nginx 뒤 서버에 이 값을 넣으면 같은 사이트 요청도 CORS 검사를 받아 막힐 수 있습니다.

FE 웹 개발 서버(`:webApp:wasmJsBrowserDevelopmentRun`)는 `http://localhost:8080`을 쓰므로, 함께 개발할 때는 백엔드를 다른 포트로 띄우고 FE 주소를 허용합니다.

```powershell
$env:SERVER_PORT = '8081'
$env:SECURITY_ALLOWED_ORIGINS = 'http://localhost:8080'
.\gradlew.bat bootRun
```

### MySQL 연결과 Flyway

모범 음원339의 V15는 객체 키 전환까지 수행하고 구 BE 호환용 `audio_url`을 남깁니다. 새 BE 배포 완료 후 [전용 후처리 도구](../infra/catalog/model-audio.md#새-be-배포-후-audio_url-삭제)로 컬럼을 삭제합니다. 이 삭제는 배포 전에 실행되는 일반 Flyway migration에 포함하지 않습니다. V15 이력/checksum은 그대로이며 이후 Flyway 검증·새 BE 조회는 컬럼 없이 동작해야 합니다.

비어 있는 MySQL 8.4 DB와 접속 계정을 먼저 준비한 뒤 아래 환경변수를 설정합니다. URL에는 실제 DB 이름을 지정합니다. 인프라 합의의 서버 DB 이름은 `eolssu`입니다.

| 환경변수 | 용도 |
| --- | --- |
| `SPRING_PROFILES_ACTIVE=db` | DataSource·MyBatis·Flyway 활성화 |
| `DB_URL` | JDBC URL. 예: `jdbc:mysql://localhost:3306/eolssu` |
| `DB_USERNAME`, `DB_PASSWORD` | 해당 DB의 접속 정보. 기본값 없음 |
| `AUTH_CODE_HMAC_SECRET` | 인증번호 HMAC 비밀키(Base64, 디코딩 후 32바이트 이상). `db`에서 필수 |
| `AUTH_MAIL_MODE` | 기본 `unavailable`, 로컬 메일 대역은 `memory` |
| `INQUIRY_MAIL_RECIPIENTS` | 문의하기를 받을 개발자 메일(쉼표 구분). 비우지 않으면 기본 주소 3개를 덮어씀 |
| `ANALYSIS_AI_BASE_URL` | 연주 분석 AI 서비스 주소. 비우면 업로드 뒤 분석을 시작하지 않음 |

```powershell
$env:SPRING_PROFILES_ACTIVE = 'db'
$env:DB_URL = 'jdbc:mysql://localhost:3306/eolssu'
$env:DB_USERNAME = 'eolssu'
$env:DB_PASSWORD = '<로컬 DB 비밀번호>'
$env:AUTH_CODE_HMAC_SECRET = '<별도로 발급해 보관한 Base64 비밀키>'
.\gradlew.bat bootRun
```

macOS / Linux에서는 같은 값을 터미널에서 `export`한 뒤 `./gradlew bootRun`을 실행합니다. JAR도 같은 환경변수로 실행합니다. `db` 프로필에서 연결·마이그레이션 검증이 실패하면 기동에 실패합니다.

- Flyway가 `src/main/resources/db/migration/`의 `V1__initial_schema.sql`, `V2__auth_user_practice_extensions.sql`, `V3__practice_session_recording_metadata.sql`, `V4__practice_session_analysis_attempt.sql`을 버전 순서대로 적용하고 `flyway_schema_history`에 기록합니다. 다시 시작하면 이력을 검증하고 이미 적용한 버전은 재실행하지 않습니다.
- 적용한 버전 파일은 수정하지 않고 `V5__설명.sql`처럼 다음 번호로 새 버전을 추가합니다. `schema.sql` 자동 초기화는 사용하지 않습니다.
- MySQL DDL은 실패해도 되돌려지지 않습니다. V2는 DDL 전에 기존 데이터를 확인해, 세마치·굿거리·중모리·타령 밖의 장단, 개인정보가 남은 탈퇴 회원, 20~240 밖의 세션 BPM이 있으면 `chk_v2_preconditions` 위반으로 스키마를 바꾸지 않고 기동에 실패합니다. 기존 장단·세션의 `bpm_unit`은 장단 이름으로 채웁니다. V4도 같은 방식으로, 실패 코드(`fail_reason`)가 6종 밖이거나 `FAILED` 여부와 실패 코드가 맞지 않는 세션이 있으면 `chk_v4_preconditions` 위반으로 멈춥니다(실패 행은 `version = '4'`). V14는 `lessons`·`lesson_progress`에 행이 있거나, 지울 V1 자동 이름(`lessons_ibfk_1`·`lessons_chk_1`·`lesson_progress_chk_1`·유일 인덱스 `user_id`)이 없으면 `chk_v14_preconditions` 위반으로 멈춥니다(실패 행은 `version = '14'`).
- 이때 `flyway_schema_history`에는 실패 행(`version = '2'`, `success = 0`)이 남아, 데이터만 고쳐 다시 시작하면 `Detected failed migration to version 2`로 다시 실패합니다. 이 프로젝트에는 Flyway CLI·Gradle 작업이 없으므로 같은 DB에 MySQL 클라이언트로 접속해 해당 행을 고친 뒤 실패 행을 지우고 `db` 프로필로 다시 시작합니다. 사전 확인을 통과한 뒤 구조 변경 중에 실패하면(연결 끊김 등) 앞 문장은 이미 반영돼 실패 행만 지우면 같은 문장에서 다시 실패합니다. 이때는 `SHOW CREATE TABLE`로 반영된 문장을 확인해 남은 문장을 적용한 뒤 실패 행을 `success = 1`로 고치고 다시 시작합니다.

```sql
SELECT version, success FROM flyway_schema_history;               -- 2, 0 확인
DELETE FROM flyway_schema_history WHERE version = '2' AND success = 0;
```
- 자동 baseline과 Flyway clean은 비활성화했습니다. 이미 테이블이 있는 DB에 이력 없이 연결하면 자동 편입하지 않고 실패합니다. 기존 DB 도입은 스키마 일치 확인과 별도 적용 계획이 필요합니다.
- 연결마다 MySQL 세션 시간대를 `+00:00`으로 맞춥니다. `DATETIME(6)` 저장 모델의 `LocalDateTime`은 UTC 값으로 취급하고 API 시간대 변환은 기능 구현에서 정합니다.

### 연습 세션 생성(PRA-01)

`db` 프로필에서 로그인한 회원이 `POST /api/practice-sessions`를 호출합니다. `Idempotency-Key` 헤더(출력 가능 ASCII 1~100자)가 필수이고, 본문은 `songId`(양의 정수 문자열)·`bpm`(20~240 정수)이 필수, `bpmUnit`·`startNoteIdx`·`endNoteIdx`·`parentSessionId`·`jangdanSoundOn`이 선택입니다. 미션은 구현하지 않기로 해서(S15P31E102-465) `missionId`를 보내도 무시합니다. JSON 타입이 명세와 다르면(`96.7`, `"96"`, 숫자 ID, `1`인 불리언 등) 자동 변환하지 않고 400입니다. 성공하면 201과 `result{sessionId,songId,status,bpm,bpmUnit,startNoteIdx,endNoteIdx,startedAt}`이며 세션은 `RECORDING`, `started_at`은 서버 UTC 시각, `score_version`은 곡의 현재 값입니다. 생략한 구간은 곡 전체, 생략한 `bpmUnit`은 곡 장단의 값입니다.

- 곡은 `PUBLISHED`·채점 가능·삭제 안 됨이어야 합니다(아니면 404). `jangdanSoundOn`은 저장하지 않습니다.
- 부모 세션은 본인 소유가 아니면 404, 다른 곡이면 400, `DONE`이 아니면 409 `STATE_CONFLICT`입니다. 곡 밖의 구간은 400입니다.
- 같은 회원·키로 같은 본문을 다시 보내면 처음 세션을 201로 돌려주고, 다른 본문이면 409 `IDEMPOTENCY_CONFLICT`입니다. 비교할 때 `startNoteIdx` 생략은 0, `jangdanSoundOn` 생략은 false와 같게 봅니다. `endNoteIdx`·`bpmUnit`은 생략과 명시를 다른 본문으로 봅니다(곡 값에 따라 달라지기 때문). 비교용 해시는 예전 미션 자리를 고정값으로 남겨, 미션 제거 전에 만든 세션도 같은 키 재전송에서 그대로 돌려줍니다. 동시에 보내도 `(user_id, idempotency_key)` 유일 제약으로 한 행만 저장됩니다.
- Swagger 요청 본문은 전체 연습(기본)·구간 연습·다시 연습 예시를 고를 수 있고(`OpenApiContracts`의 `requestExamples`), Schema 탭에 필드별 뜻과 생략 시 값을 적었습니다. 예시의 `songId` 15는 테스트 서버의 채점 가능 곡이라 로컬 시드 DB(1~4번 곡)에서는 바꿔서 보냅니다.
- V25는 `practice_sessions.mission_id`를 지웁니다. 이전 백엔드는 이 칸을 조회하므로 먼저 호환 백엔드를 배포해야 합니다. S15P31E102-499의 1차 호환 릴리스는 실행 JAR에서만 V25를 제외했고 운영 세션 생성·조회·멱등 재전송을 확인했습니다. 후속 릴리스는 임시 `bootJar` 블록을 제거해 실행 JAR에도 원본 V25를 그대로 포함합니다. 운영 승인·적용 결과는 작업 기록과 구분해 확인합니다. [단계별 배포·복구 절차](../infra/runtime/README.md#v25-삭제-전-호환-백엔드-배포)를 따릅니다.
- 결정 근거는 [작업 계획](../docs/plans/S15P31E102-217.md)에 있습니다. `databaseTest`의 `PracticeSessionApiDatabaseTests`가 실제 HTTP·서명 쿠키로 확인합니다.

### 실제 MySQL 통합 검사

담당 개발자가 저장 계층 변경 후, MR 병합 전에 실행합니다. **운영·공유 개발 DB가 아닌 비어 있는 폐기 가능한 MySQL 8.4 DB**를 준비합니다. V1~V14 DDL·시드와 Flyway 이력은 남고, 검사 데이터는 테스트 트랜잭션 종료 시 롤백됩니다. 검사는 DB를 자동 생성하거나 삭제하지 않습니다.

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/eolssu_test'
$env:TEST_DB_USERNAME = 'eolssu_test'
$env:TEST_DB_PASSWORD = '<테스트 DB 비밀번호>'
.\gradlew.bat databaseTest
```

macOS / Linux에서는 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`를 `export`한 뒤 `./gradlew databaseTest`를 실행합니다. 이 검사는 앱용 `DB_*` 대신 `TEST_DB_*`로 접속하며 세 변수가 없으면 실패합니다. 보고서는 `build/reports/tests/databaseTest/index.html`입니다. 재설정·탈퇴 일괄 삭제 동시성 검사는 고정 지연 대신 실제 잠금 대기를 관측하므로, 테스트 계정은 `performance_schema.data_lock_waits`와 `performance_schema.data_locks`의 SELECT 권한도 필요합니다. 변환 대기 검사는 대기 중인 연결을 찾으려고 `performance_schema.threads`도 읽으므로 같은 SELECT 권한이 필요합니다. 이 권한들은 테스트 DB 계정용이며 운영 앱 계정에 추가하지 않습니다.

Docker가 있으면 `python3 -B ../infra/jenkins/run-database-tests.py -- ./gradlew databaseTest`(backend 폴더에서 실행)로 폐기용 MySQL 8.4 컨테이너를 띄워 같은 검사를 돌릴 수 있습니다. 컨테이너는 임의의 loopback 포트·tmpfs 데이터로 뜨고, 실행마다 만든 비밀번호를 환경변수로만 넘기며, 검사가 실패하거나 중단돼도 지웁니다. Jenkins backend 잡도 develop 변경마다 이 방식으로 `databaseTest`를 실행합니다([S15P31E102-946](../docs/plans/S15P31E102-946.md)). 발표(11-18)까지는 실패해도 배포를 막지 않고 빌드를 UNSTABLE로 표시해 알림만 보내므로, 저장 모델·쿼리·마이그레이션 변경은 병합 전에 직접 실행하는 것을 계속 권장합니다.

검사 범위는 MySQL 8.4 V1~V14 적용·재실행·검증, 19개 테이블의 Mapper Bean·XML·생성자 매핑, nullable 값·JSON·한글/한자·전각 기호·마이크로초·UTC 세션, FK·CHECK와 V2의 회원 상태·간편 로그인 연결·약관·인증·토큰·곡별 BPM·연습 기록 중복 키·체류 기록 제약과 V3 녹음 메타데이터의 전부 NULL 또는 전부 양수 제약, V4 분석 시도·실패 코드 제약(위반 제약 이름까지 확인), V5 단소 율명 시드, V6 단소 악기·장단·4곡 시드가 연습 세션 조건(`PUBLISHED`·채점 가능)을 갖추고 4곡 악보 전체가 음표로 풀리는지, V14 학습 트랙 4개×9단계 시드와 트랙·단계 유일·트랙·판정 방식 제약, 학습 단계 API의 실제 HTTP 조회·완료 표시(본인 표시만·처음 시각 유지·동시 표시 한 행)와 목록 조회의 인덱스 사용, 곡 악보를 연습 구간 음표로 푸는 동작, 탈퇴 일괄 삭제에서 회원 세션을 한 문장으로 지우는 동작, 체류 구간의 회원·키별 1회 저장과 마이크로초 왕복, 분석 결과를 같은 시도의 마감 전 `PENDING` 세션에만 저장하고 마감을 넘긴 `PENDING`만 `TIMEOUT`으로 바꾸며 음표별 결과 저장이 실패하면 상태 변경까지 되돌리는 동작, 녹음 업로드의 조건부 갱신(회원 본인의 `RECORDING` 세션만 원본·메타데이터·`ended_at` 기록, `PENDING` 전환, 분석 시도 시작, 같은 트랜잭션에서 `RecordingUploaded` 발행), KST 날짜별 연습 시간·`DONE` 세션 합산과 연습한 날 조회, 성장 요약·날짜별 API의 실제 HTTP·JWT 쿠키 조회, 분석 다시 시도를 본인의 녹음이 있는 `TIMEOUT`·`SERVER_ERROR` 세션에만 시작하고 동시 요청 두 건 중 하나만 새 시도로 바꾸는 동작입니다. 되돌림·동시 요청 검사는 테스트 트랜잭션 밖에서 커밋하고 넣은 행을 지웁니다. 기본 `test`와 `clean build`에는 이 DB 검사를 포함하지 않고, Jenkins는 별도 `Backend database tests` 단계에서 실행합니다.

### 녹음 파일 검사와 ffmpeg

업로드된 녹음이 [녹음 파일 규격(PRA-02)](../docs/specs/recording-file.md)에 맞는지 `RecordingFileValidator`가 판정합니다. 크기(20MiB 초과면 `TooLarge`)를 먼저 보고, 실제 내용을 `ffprobe`로 읽어 MP4 계열 컨테이너의 AAC 오디오 또는 WAV 컨테이너의 PCM16(`pcm_s16le`, 웹 녹음) 오디오인지(`UnsupportedFormat`), 길이가 1ms 이상·601초 미만인지(`InvalidDuration`) 봅니다. 1ms 미만은 저장할 길이가 0ms로 반올림돼 V3 메타데이터 CHECK에 걸리므로 먼저 거부합니다. 확장자·MIME는 보지 않습니다. 오디오 스트림이 없는 m4a는 길이 검사 전에 `UnsupportedFormat`이 됩니다. `ffprobe` 실행 실패·10초 초과·대기 중 중단·출력 읽기 실패는 규격 위반이 아니라 `AudioProbeException`이며, 남은 ffprobe 프로세스는 종료합니다. multipart 한도는 스펙대로 파일·요청 25MiB이고, 20MiB 경계는 검사기가 정합니다(Nginx `client_max_body_size 25m`은 업로드 API Task에서 설정).

WAV는 `UploadRecording`이 `FfmpegRecordingTranscoder`로 모노 AAC(64kbps) m4a로 바꾼 뒤 변환본을 같은 검사기로 다시 판정하고, 변환본과 그 메타데이터를 저장합니다(S15P31E102-1272). ffmpeg가 읽지 못하거나 손상된 WAV(`-xerror`)는 415이고, ffmpeg 실행 실패·30초 초과·대기 중 중단은 규격 위반이 아니라 `RecordingTranscodeException`(500)입니다. 업로드 WAV와 변환본 임시 파일은 요청이 끝나면 지웁니다.

`mediaTest`는 테스트 중 `ffmpeg`로 사인파·무음 AAC(2초, 600.5초, 601초), 이름만 바꾼 MP3, ALAC, 오디오 없는 m4a, 잘린 파일, PCM16·float WAV를 만들어 `ffprobe`로 검사하고, 16kHz 모노 WAV(2초·600초)와 48kHz 스테레오 WAV를 실제로 변환해 변환본을 검사합니다. 큰 파일은 커밋하지 않습니다. `ffmpeg`·`ffprobe`가 `PATH`에 없으면 건너뛰지 않고 실패합니다. 기본 `test`·`clean build`는 테스트 JVM의 `java`로 띄우는 가짜 ffprobe·ffmpeg(`FakeFfprobe`)로 출력 해석·종료 코드·시간 초과·중단 시 프로세스 정리를 검사하므로 OS와 ffmpeg 설치에 관계없이 돕니다.

| 환경 | 설치 |
| --- | --- |
| macOS | `brew install ffmpeg` |
| Ubuntu | `sudo apt-get install ffmpeg` |
| Windows | [FFmpeg 공식 빌드](https://ffmpeg.org/download.html)를 받아 `bin`을 `PATH`에 추가 |
| 실행 이미지 | `Dockerfile` 실행 단계에서 `ffmpeg=7:8.0.1-3ubuntu2`(베이스 `eclipse-temurin:21-jre`의 Ubuntu 26.04 패키지)를 고정 설치 |

고정 버전은 베이스 이미지의 Ubuntu 버전이 바뀌면 설치에 실패하므로 함께 갱신합니다. ffmpeg 설치로 실행 이미지가 약 410MB 커집니다. 운영 CI([infra/jenkins/backend.Jenkinsfile](../infra/jenkins/backend.Jenkinsfile))는 `mediaTest`를 실행합니다. 운영 이미지를 만드는 서버의 `/opt/eolssu/config/backend.Dockerfile`도 이 파일과 맞춥니다.

### 녹음 업로드와 원본 저장소(PRA-02)

`POST /api/practice-sessions/{sessionId}/recording`(`db` 프로필, 로그인 필요)은 multipart의 `audio` 파트 하나를 받아 [녹음 파일 규격](../docs/specs/recording-file.md)의 순서(404→410→409→413→415→422→503→500)로 판정합니다. 통과하면 원본을 `recordings/{sessionId}/{UUID}.m4a`에 저장하고, 세션이 아직 `RECORDING`일 때만 한 번의 조건부 갱신으로 원본 키·메타데이터 4칸·`ended_at`(서버 UTC)을 기록하고 `PENDING`으로 바꾸며 분석 시도를 시작합니다(`analysis_attempt` + 1, `analysis_started_at`). 응답은 202이고 본문은 `{sessionId, status: "PENDING", evaluationId: null, pollAfterMs: 2000}`입니다. 앱이 보내는 `endedAt`과 `Idempotency-Key`는 읽지 않습니다. 저장에 성공한 세션에 다시 올리면 409이며, 응답을 받지 못한 앱은 상태 조회로 결과를 확인합니다.

거부 코드(`result.errorCode`)는 404 `SESSION_NOT_FOUND`, 410 `SESSION_EXPIRED`, 409 `SESSION_NOT_RECORDING`, 413 `RECORDING_TOO_LARGE`, 415 `RECORDING_UNSUPPORTED_FORMAT`, 422 `RECORDING_INVALID_DURATION`, 503 `RECORDING_STORAGE_UNAVAILABLE`, 500 `INTERNAL_SERVER_ERROR`입니다. DB 갱신이 실패하거나 동시 업로드에서 지면 자기 요청이 만든 객체만 지웁니다. 조건부 갱신과 같은 트랜잭션에서 `RecordingUploaded(sessionId)`를 발행하고, 분석은 아래 `AnalysisOnUpload`가 커밋 뒤 백그라운드에서 시작합니다. AI가 원본을 읽는 15분짜리 서명 URL은 `RecordingReadUrls` 빈이 만듭니다. 서명 URL은 녹음 접근 토큰이라 로그에 남기지 않습니다.

원본 저장소는 S3 호환 API로 연결하고 처음 쓰는 업체는 Cloudflare R2입니다. 아래 다섯 값이 모두 있어야 연결하며, 하나라도 비면 기동은 하되 업로드를 503으로 답합니다. 키는 서버 `.env`에만 두고 커밋하지 않습니다.

| 변수 | 값 |
| --- | --- |
| `RECORDING_STORAGE_ENDPOINT` | R2 S3 API 주소 `https://<account_id>.r2.cloudflarestorage.com` |
| `RECORDING_STORAGE_BUCKET` | 녹음 버킷 이름 |
| `RECORDING_STORAGE_ACCESS_KEY_ID`·`RECORDING_STORAGE_SECRET_ACCESS_KEY` | 이 버킷 하나에 객체 읽기·쓰기만 허용한 R2 API 토큰의 S3 키 |
| `RECORDING_STORAGE_REGION` | `auto`(기본값) |

R2 버킷 준비(계정 소유자, Cloudflare 대시보드):

1. R2 > 버킷 만들기: 위치 힌트 `Asia-Pacific(apac)`, 기본 저장 등급 `Standard`.
2. 버킷 설정 > 공개 접근: `r2.dev` 주소와 사용자 도메인을 모두 끈 상태로 둡니다.
3. 버킷 설정 > 객체 수명 주기 규칙: 두지 않습니다. 저장하지 않은 녹음은 서버 정리 작업(`UnsavedRecordingPurgeService`, 매일 04:30 KST)이 업로드 30일 뒤 지우고, 저장한 기록의 녹음은 회원이 지우거나 탈퇴할 때까지 보관합니다(S15P31E102-375, [녹음 보관](../docs/specs/recording-retention.md)). 375 전환 전 운영 버킷에는 `recordings/` 30일 삭제 규칙이 있었고 전환 때 지웁니다([전환 절차](../infra/storage/README.md#저장한-기록-녹음-보관-전환-s15p31e102-375)).
4. R2 > API 토큰 관리 > API 토큰 만들기: 권한 `Object Read & Write`, 적용 버킷은 이 버킷 하나로 지정합니다. 발급된 Access Key ID·Secret Access Key와 S3 API 주소를 서버 `.env`에 넣습니다.

샘플레이트·채널 수를 양수로 읽지 못한 AAC는 415로 거부합니다(메타데이터 4칸은 전부 양수여야 저장됨). 저장소 호출은 재시도를 포함해 40초에서 끊어 503으로 답합니다. ffprobe 상한 10초를 더해도 Nginx `proxy_read_timeout` 기본값 60초보다 짧게 두어, 느린 저장소가 Nginx 504가 되지 않게 합니다. WAV 업로드는 변환(상한 30초)과 변환본 ffprobe가 더해져 모든 상한이 겹치면 60초를 넘을 수 있습니다. 이때는 Nginx 504가 되고 서버는 처리를 이어 가므로, 앱의 재전송이 409를 받아 상태 조회로 결과를 확인합니다.

S3 클라이언트는 AWS SDK for Java v2를 경로 방식(`엔드포인트/버킷/키`)으로 쓰고, SDK 기본값인 요청마다의 CRC 체크섬은 업체마다 지원이 달라 필요한 요청에만 붙입니다(`WHEN_REQUIRED`). `http://` 엔드포인트에서는 SDK가 본문을 서명 청크(`aws-chunked`)로 보내므로, 로컬 대역 서버를 만들 때 이를 풀어야 합니다. 자동 검사(`S3RecordingStorageTests`)는 로컬 HTTP 서버를 S3 엔드포인트로 두고 요청 경로·서명·서명 URL 형식을 확인하며 실제 R2에 연결하지 않습니다. 실제 R2 확인(쓰기, 서명 URL로 자격 증명 없이 읽기, 15분 뒤 거부, 삭제)은 테스트 전용 버킷을 만든 뒤 수동으로 합니다. 2026-10-29에는 사용자 승인으로 운영 버킷의 전용 합성 객체를 사용해 연결·정책을 검증하고 버킷명 오류·누락된 보관 규칙을 수정했습니다([검증 기록](../docs/knowledge/infra/r2-storage-verification.md)).

Nginx(`infra/runtime/nginx.conf`·`nginx.tls.conf.template`)는 `/api/`의 본문 한도를 25MiB(`client_max_body_size 25m`)로 둡니다. 25MiB를 넘으면 Nginx가 자체 HTML 413을, 20MiB 초과~25MiB는 백엔드가 공통 오류 형식 413을 돌려줍니다.

## 폴더와 코드 배치

```text
backend/
├── AGENTS.md / CLAUDE.md
├── settings.gradle.kts        # 독립 프로젝트 eolssu
├── build.gradle.kts
├── gradle.properties
├── gradlew / gradlew.bat
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── .env.example
├── src/main/
│   ├── kotlin/com/ssafy/eolssu/backend/
│   │   ├── BackendApplication.kt
│   │   ├── config/                # 구현 연결·스케줄링·보안 설정 (SecurityConfig, SecurityProperties)
│   │   ├── common/error/          # 공통 오류 응답·예외 처리
│   │   ├── common/security/       # CSRF 필터
│   │   └── feature/<기능>/
│   │       ├── application/       # 구현이 있는 기능만 (예: practice)
│   │       ├── domain/
│   │       └── infrastructure/
│   │           ├── persistence/   # <테이블명>Row.kt, <테이블명>Mapper.kt
│   │           └── media/·storage/ 등  # 외부 도구·저장소 구현 (예: practice의 ffprobe, S3 호환 원본 저장소)
│   └── resources/
│       ├── application.yml / application-db.yml
│       ├── db/migration/V1__initial_schema.sql, V2__auth_user_practice_extensions.sql, V3__practice_session_recording_metadata.sql, V4__practice_session_analysis_attempt.sql
│       └── mybatis/<기능>/<테이블명>Mapper.xml
├── src/test/kotlin/com/ssafy/eolssu/backend/
│   ├── BackendApplicationTests.kt
│   └── DatabaseIntegrationTests.kt
├── src/test/resources/db/mapper-fixtures.sql
└── README.md
```

실제 기능 코드는 다음 기준으로 추가합니다. 아직 구현이 없는 패키지와 빈 클래스는 생성하지 않습니다.

```text
com.ssafy.eolssu.backend/
├── config/                    # Spring 설정·최종 의존성 조립
├── common/error/              # 실제로 공유하는 오류 응답·예외 처리
├── common/security/           # 여러 기능이 함께 쓰는 보안 필터
└── feature/<기능명>/
    ├── api/                   # Controller·공개 요청/응답 DTO
    ├── application/           # 서비스·처리 흐름
    ├── domain/                # 업무 모델·규칙·인터페이스
    └── infrastructure/        # 저장소·외부 호출 구현
```

의존 방향은 `api → application → domain`, `infrastructure → domain`으로 두며 `config`에서 구현을 연결합니다. `domain`은 Spring/MyBatis/HTTP 구현에, `application`은 구체적인 `infrastructure` 구현에 의존하지 않습니다. 기능 간 협력은 application의 공개 계약으로 한정하고 순환 참조를 피합니다. 같은 폴더 안의 패키지 경계는 Gradle이 자동 강제하지 않으므로 실제 기능 추가 시 필요한 검증을 함께 구성합니다.

Mapper는 기본적으로 PK 조회 `findById`를 제공하고, 기능 구현에 필요한 조회·갱신을 더합니다(예: `PracticeSessionMapper.failRecordingStartedBefore`). `findById`는 존재하지 않는 ID에 `null`을 반환하며, 삭제·공개 상태 필터는 적용하지 않습니다. Row는 저장 계층 전용이므로 API 응답으로 그대로 노출하지 않습니다. 기능 구현 시 domain 저장소 계약과 infrastructure 구현을 연결합니다.

`db`·`ops` 프로필에서는 녹음 업로드 만료 작업이 5분마다 실행됩니다. `RECORDING`인 채 `started_at` + 40분이 지난 연습 세션을 `FAILED`·`UPLOAD_TIMEOUT`으로 바꿉니다([녹음 파일 규격](../docs/specs/recording-file.md), S15P31E102-168에서 develop 병합 전). 무DB 기본 프로필에서는 만들지 않습니다. 주기 실행은 `eolssu.scheduling.enabled=false`로 끌 수 있으며, `databaseTest`는 공유 테스트 DB와 겹치지 않도록 이 값을 끕니다.

`growth`에는 악보 화면 체류 구간 기록 API `POST /api/users/me/score-views`(GRO-04)와 그 검증·중복 처리·저장소, 연습 기록지 요약(GRO-01, `GET /api/users/me/growth/summary`)·날짜별 값(GRO-02, `GET /api/users/me/growth/calendar`)·곡별 최근 점수 변화(GRO-03, `GET /api/users/me/growth/scores`) API가 있습니다. `db` 프로필에서 조회 API는 `GrowthStatsConfig`, 체류 기록 API는 `ScoreViewConfig`로 연결하고 access_token의 회원으로 처리합니다. 체류 기록 요청·응답 계약은 [연습 시간 집계 기준](../docs/specs/practice-time.md#체류-기록-api-gro-04)을 따릅니다. 요약의 곡별 최고점·항목별 성장은 기간과 관계없이 회원의 전체 곡 연주(첫 음~끝 음)로 냅니다. 점수 변화는 곡 하나의 전체 곡 연주 또는 구간 연습 점수를 최근 순으로 골라 오래된 순서로 주며, 각 번호는 EVA-03과 같은 `EvaluationScoreCells`로 읽습니다([곡별 최근 점수 변화 조회 API](../docs/specs/growth-score-trend.md)).

`lesson`에는 기초부터 배우기 단계 목록·진도(LRN-01, `GET /api/lessons?track=`)·단계 상세(LRN-02, `GET /api/lessons/{lessonId}`)·완료 표시·해제(LRN-03, `PUT /api/lessons/{lessonId}/completion`) API가 있습니다. `db` 프로필에서 `LessonConfig`로 연결하고 access_token의 회원 진도만 읽고 바꿉니다. 단계는 V14가 넣은 학습 트랙 4개×9단계이며 계약은 [학습 단계·진도 API](../docs/specs/learning-progress-apis.md)를 따릅니다.

`analysis`에는 업로드된 연습 녹음을 AI로 분석하는 흐름(PRA-03 [분석 요청과 상태 조회 계약](../docs/specs/practice-analysis-failure.md))이 있습니다. `HttpAnalysisClient`가 AI의 `POST /v1/analyses`를 부르고, `MyBatisPracticeAnalysisRepository`가 같은 시도의 마감 전 `PENDING` 세션에만 결과를 저장합니다. `AnalyzePracticeSession`은 실패를 `SERVER_ERROR`, 제한 시간(기본 60초)을 넘긴 시도를 `TIMEOUT`으로 기록합니다. 한 흐름이 `practice_sessions`·`evaluations`·`note_results`를 함께 바꾸므로 `PracticeAnalysisMapper` 하나에 모았습니다. `db` 프로필의 분석 실행·AI 클라이언트 연결은 아래 `AnalysisOnUpload` 설명을 따릅니다.

`practice`의 `GET /api/practice-sessions/{sessionId}`(`PracticeSessionController`)는 API 명세의 연습 세션·분석 상태 조회입니다. 회원 본인의 세션만 돌려주고(없거나 남의 세션, 기록을 지운 세션은 404 `RESOURCE_NOT_FOUND`), 제한 시간을 넘긴 `PENDING`은 응답 전에 `TIMEOUT`으로 바꿉니다(`GetPracticeSession`). 저장한 기록을 처음 조회하면 `viewed_at`을 한 번 기록해 연습 기록 목록의 NEW 표시를 끕니다.

업로드 뒤 분석은 `AnalysisOnUpload`가 시작합니다. 업로드는 `PENDING`으로 바꾸는 트랜잭션 안에서 `RecordingUploaded(sessionId)`를 발행하고, 커밋 뒤에 `analysisExecutor`(기본 2개 동시·대기 50개)에서 `AnalyzePracticeSession`이 돕니다. 롤백된 업로드나 트랜잭션 밖 발행은 분석하지 않습니다. 음표는 `MyBatisPracticeScoreNotes`가 곡 악보(공통 악보 데이터)의 `NOTE`만 0부터 세어 연습 구간을 고르고, `pitches`로 반음·음 ID를 채웁니다. 이 연결은 `PracticeScoreNotes`·`RecordingReadUrls` 구현 Bean과 `ANALYSIS_AI_BASE_URL`이 모두 있을 때만 켜지며, 없으면 경고 로그만 남기고 세션은 제한 시간 뒤 `TIMEOUT`이 됩니다. 설정은 `eolssu.analysis.*`(`time-limit` 기본 60초, `ai-response-timeout` 기본은 제한 시간 + 5초이며 제한 시간보다 길어야 함, `workers`, `queue-capacity`)입니다. 두 시간은 숫자만 쓰면 초로 읽습니다.

| 기능 패키지 | 테이블 |
| --- | --- |
| `pitch`, `instrument`, `fingering` | `pitches`, `instruments`, `fingering_charts` |
| `jangdan`, `song`, `notation` | `jangdans`, `songs`, `notation_symbols` |
| `user`, `practice` | `users`, `user_song_settings`, `practice_sessions` |
| `auth`, `term`, `growth` | `user_social_accounts`, `email_verifications`, `refresh_tokens`, `terms`, `user_term_agreements`, `score_views` |
| `evaluation` | `evaluations`, `note_results` |
| `analysis` | 분석 결과 저장(`practice_sessions` 상태, `evaluations`, `note_results`) |
| `record` | 연습 기록 칸(`practice_sessions`의 `saved_at`·`memo`·`viewed_at`·`record_deleted_at`, 녹음 경로 비우기) |
| `lesson` | `lessons`, `lesson_progress` |

Mapper는 `@Mapper`로 검색하고 XML namespace는 인터페이스 전체 이름, statement ID는 메서드 이름과 맞춥니다. Kotlin 생성자 매핑을 위해 `javaParameters`, `arg-name-based-constructor-auto-mapping`, `map-underscore-to-camel-case`를 사용합니다. XML은 조회 열을 명시합니다. JSON 열은 원문 `String`, 상태 열은 `String`, nullable 열은 Kotlin nullable로 보존하며 도메인 정책은 추가하지 않습니다.

Docker 이미지·개발/운영 Compose·Nginx·백엔드 Jenkins 구성과 실행 방법은 [인프라 실행 문서](../infra/README.md)를 따릅니다. Docker 빌드는 `docker build -f backend/Dockerfile --tag eolsu-backend:check .`를 저장소 루트에서 실행합니다. 실제 Jenkins 잡 등록·서버 배포는 별도 작업입니다.

인증은 토큰 검증·약관 목록·이메일 인증까지 포함하고, 가입·로그인 등 나머지 회원 API(S15P31E102-207~211)와 업무 API·운영 DB 적용은 아직 포함하지 않습니다. AI 호출은 클라이언트만 있고 앱에 연결하지 않았습니다. 실행·검증 방법은 이 README, 조사 결과는 루트 `docs/knowledge/backend/`, 공통 계약은 `docs/contracts/`, 기술 결정은 `docs/decisions/`에 기록합니다.

## 작업 기준

- Jira: [S15P31E102-60](https://ssafy.atlassian.net/browse/S15P31E102-60)
- 저장 계층: [S15P31E102-74](https://ssafy.atlassian.net/browse/S15P31E102-74) · [MyBatis·Flyway 구성 근거](../docs/knowledge/backend/mybatis-flyway-bootstrap.md)
- V2 ERD 보완: [S15P31E102-142](https://ssafy.atlassian.net/browse/S15P31E102-142) · [보완안·검증 기록](../docs/plans/S15P31E102-142.md)
- V3 녹음 원본 메타데이터: [S15P31E102-179](https://ssafy.atlassian.net/browse/S15P31E102-179) (PRA-02 녹음 파일 규격, S15P31E102-168)
- V4 분석 시도·실패 코드 제약: [S15P31E102-173](https://ssafy.atlassian.net/browse/S15P31E102-173) · [작업 기록](../docs/plans/S15P31E102-173.md)
- V5 단소 율명(`pitches`) 시드: [S15P31E102-186](https://ssafy.atlassian.net/browse/S15P31E102-186) · [PRA-03 스펙](../docs/specs/practice-analysis-failure.md)
- V6 단소 악기·장단·1차 4곡 시드: [S15P31E102-249](https://ssafy.atlassian.net/browse/S15P31E102-249) · [작업 기록](../docs/plans/S15P31E102-249.md)
- 체류 구간 기록: [S15P31E102-189](https://ssafy.atlassian.net/browse/S15P31E102-189) · [작업 기록](../docs/plans/S15P31E102-189.md)
- 연습 기록 저장·목록·재생·삭제(REC-01~04): [S15P31E102-342](https://ssafy.atlassian.net/browse/S15P31E102-342) · [작업 기록](../docs/plans/S15P31E102-342.md)
- 구조안: [Notion 폴더 구조](https://app.notion.com/p/401a0252aa458076afe3e2ff12e6e5b1)
- 초기화 방법: [Spring Boot + Kotlin 공식 가이드](https://spring.io/guides/tutorials/spring-boot-kotlin/)

### 간편 로그인·계정 연결 (S15P31E102-211)

Android App Links 문서는 `GET /.well-known/assetlinks.json`으로 제공합니다. `AUTH_ANDROID_APP_SHA256_FINGERPRINTS`에 실제 설치 APK의 공개 SHA-256 인증서 지문을 쉼표로 구분해 설정합니다.64자리16진수 또는 콜론으로 구분한32바이트를 받으며, 잘못된 값은 기동 시 거절합니다. 일반 앱 package_name은 `com.ssafy.eolssu`이며 QA 앱 설정과 미설정 처리 기준은 아래를 따릅니다. 설정 시200 application/json 원시 배열이며 로그인 쿠키가 필요 없습니다. 실제 지문/환경변수 주입과 Nginx 배포·기기 검증 전에는 App Links가 준비된 상태가 아닙니다. API5필드 성공 형식의 예외는 이 Android 공개 검증 프로토콜에만 적용합니다.

현재는 서버 코드와 대역 기반 검증 단계입니다. 사용자 확인(2026-10-29): 제공자 키는 아직 발급하지 않았습니다. 실제 사용 완료 조건은 제공자별 앱 등록·키 발급, FE 콜백 URI 합의/콘솔 등록, 서버 환경변수 주입, 실제 계정으로 로그인·연결·해제 확인입니다. 자동 테스트의 가짜 인가 코드·사용자 응답·토큰은 실제 제공자 인증 성공을 뜻하지 않습니다.

카카오·구글·네이버별 `AUTH_OAUTH_{KAKAO|GOOGLE|NAVER}_CLIENT_ID`, `_CLIENT_SECRET`, `_REDIRECT_URIS`를 설정합니다. 콜백 목록은 앱 콘솔·FE와 합의한 정확한 URI를 쉼표로 구분하며 기본 주소는 없습니다. 하나라도 비어 있으면 서버는 기동하되 해당 제공자 시작·완료만503입니다. 외부 호출은 고정 제공자 엔드포인트만 사용하고 연결3초·읽기5초 제한을 둡니다. HTTP 리다이렉트는 따라가지 않습니다.

로그인은 `POST /api/auth/oauth/{provider}/start`, 로그인 회원의 연결은 `POST /api/users/me/social-accounts/{provider}/start`에 `{ "redirectUri": "등록한 정확한 주소" }`를 보냅니다. `provider`는 소문자 kakao/google/naver입니다. 응답 `authorizationUrl`을 브라우저로 열고, FE 콜백에서 받은 code·state와 같은 redirectUri를 기존 완료 API에 전달합니다. start 응답의 `oauth_flow_{provider}` HttpOnly·SameSite=Lax·Path=/api 쿠키를 유지해야 합니다. Android도 시작·완료에 같은 쿠키 저장소를 써야 합니다. state는10분·1회용이고 제공자·목적·회원·브라우저·콜백에 결속합니다. Google에는 S256 PKCE도 적용합니다. 같은 제공자의 시작을 다시 호출하면 브라우저 쿠키가 교체되어 이전 흐름은 완료할 수 없습니다.

연결된 소셜 ID는 기존 회원으로 로그인하고 요청 후 30일 미만인 탈퇴 대기를 취소합니다. 소셜 로그인·병합도 회원 잠금 이후의 시각으로 만료를 검사하며, 만료 정각부터 401 INVALID_CREDENTIALS로 거절합니다. 미연결이며 같은 이메일의 회원이 있으면409 ACCOUNT_MERGE_AVAILABLE의 details에10분 mergeTicket을 반환합니다. POST /api/auth/oauth/merge로 명시적으로 동의해야 하며, 제공자가 확인하지 않은 이메일은 기존 비밀번호 또는 ACCOUNT_MERGE 완료 토큰이 필요합니다. 비밀번호는 회원별 독립15분 내5회 실패·15분 잠금이며 잠겨도 이메일 인증은 허용합니다. 이메일이 없거나 같은 회원이 없으면401 SOCIAL_ACCOUNT_NOT_REGISTERED이며 신규 회원은 만들지 않습니다. 마이페이지 POST/DELETE /api/users/me/social-accounts/{provider}는 연결/해제이고, 내 정보의 linkedProviders로 목록을 조회합니다.

V9 `oauth_proofs`에는 토큰·쿠키 원문 대신 SHA-256과 소비 여부를 저장합니다. 실패한 병합 트랜잭션은 티켓·이메일 토큰 소비·연결·로그인 발급을 함께 되돌립니다. 코드·제공자 토큰·mergeTicket·결속 쿠키·비밀키는 로그나 문서에 복사하지 마세요. 만료 행은 매분500개 이내로 삭제합니다. 실제 앱 등록·콜백 URI 합의·실제 제공자 통합 확인은 별도이며, 자동 검사는 외부 네트워크 대신 RestClient 대역과 전용 MySQL을 사용합니다.

### Swagger UI와 OpenAPI 조회

`db`·`ops` 프로필에서 현재 구현된 업무 API 전체를 문서화합니다. 무DB 기본 프로필에서는 꺼져 있습니다.

- UI: `/api/swagger-ui.html` → `/api/swagger-ui/index.html`
- JSON: `/api/v3/api-docs`
- 배포 후 주소: https://eolssu.site/api/swagger-ui.html

문서·UI 정적 파일·설정 JSON의 GET 조회는 로그인 없이 가능합니다. access_token 쿠키가 없거나 잘못됐거나 만료돼도 문서는 열립니다. 실제 회원 API의 인증과 ACTIVE 회원 검증은 유지합니다. Try it out으로 회원 API를 호출하려면 같은 출처의 앱에서 먼저 로그인합니다. 다른 HTTP 클라이언트의 로그인 쿠키는 브라우저와 공유되지 않습니다.

Swagger UI의 Try it out은 브라우저가 보관한 HttpOnly 쿠키를 전송합니다. OpenAPI에는 accessCookie·refreshCookie를 표시하지만 Authorize 입력으로 브라우저의 Cookie 헤더/HttpOnly 쿠키를 설정할 수는 없습니다. Bearer 헤더 인증을 추가하지 않았으며 비밀번호·실제 토큰을 문서에 저장하지 않습니다. 운영 HTTPS에서는 Secure 쿠키를 사용합니다.

Nginx의 기존 `/api/` 프록시 안에서 UI·JS·CSS·JSON을 모두 제공하므로 별도 루트 경로를 열지 않습니다. 사용자 ID 등 내부 Jwt principal은 요청 파라미터가 아닙니다. 인증·Origin·multipart 한도와 실제 업무 응답은 그대로입니다.

요청 DTO와 매핑은 springdoc으로 읽고, `OpenApiContracts`/`OpenApiConfig`가 `ResponseEntity<*>`로 소실되는 실제 결과 DTO·공통 오류·응답 상태를 보완합니다. API를 추가/변경할 때 이 계약도 갱신합니다. `OpenApiDatabaseTests`/`OpenApiOpsDatabaseTests`는 업무 컨트롤러의 실제 매핑과 생성된 문서 전체가 일치하는지, 쿠키 인증·UI 자산·nullable/204·multipart 스키마를 검증합니다. 실행은 위의 `databaseTest`를 사용합니다.

브라우저 Cookie 인증의 제약은 [Swagger 공식 안내](https://swagger.io/docs/specification/v3_0/authentication/cookie-authentication/)를 참고합니다. 외부 온라인 validator는 끄고 생성 문서는 로컬 테스트로 검증합니다.

재발급·로그아웃·분석 재시도처럼 업무 본문이 없는 POST도 기존 필터상 `Content-Type: application/json`이 필요합니다. Swagger 문서는 선택적인 빈 JSON `{}` 예시를 제공해 Try it out이 이 헤더를 설정하도록 합니다. 서버의 요청 본문 요구/업무 동작을 바꾼 것은 아닙니다.

### 연주 평가 결과 조회

인증된 `GET /api/practice-sessions/{sessionId}/evaluation`은 본인 미삭제 DONE 세션의 저장 점수·음표 판정을 반환합니다. 미완료는 409, 타인·삭제·없는 세션은 같은 404입니다. 판정은 noteIdx 순서이며 점수를 재계산하지 않습니다. [검증·계약](../docs/plans/S15P31E102-257.md).

S15P31E102-304부터 AI의 `tuningOffsetCent`와 `tuningOutOfRange`를 V11의 nullable 컬럼에 저장하고 평가 조회에 그대로 반환합니다. 기존 행과 두 필드가 없는 이전 AI 응답은 null입니다. 범위 초과 편차도 원래 값을 보존하며 점수를 다시 계산하지 않습니다. [계약](../docs/specs/practice-evaluation-api.md)을 따릅니다. 운영은 Flyway가 꺼져 있으므로 새 백엔드 배포 전에 V11을 적용해야 합니다.

### 연습 기록(REC-01~05)

`record`는 기록 저장·메모 수정(`PUT /api/practice-sessions/{sessionId}/record`), 곡별 목록(`GET /api/users/me/practice-records`), 녹음 다시 듣기 주소(`GET /api/practice-sessions/{sessionId}/recording`), 기록 삭제(`DELETE /api/practice-sessions/{sessionId}/record`)를 제공합니다(`db`·`ops` 프로필). 새 Flyway 버전 없이 V2의 기록 칸을 씁니다. 녹음 저장소(PRA-02)의 15분 서명 URL과 삭제는 `PracticeRecordConfig`가 함수로 연결합니다. 저장소를 쓸 수 없으면 다시 듣기와 녹음이 있는 기록의 삭제가 503입니다. 로컬 저장소(`RECORDING_STORAGE_TYPE=local`)의 읽기 주소는 AI용 내부 주소라 다시 듣기는 항상 503입니다. 삭제는 녹음 파일을 먼저 지우고 DB를 조건부로 갱신하며, 평가 점수는 남아 성장 집계는 바뀌지 않습니다. 저장한 녹음을 계속 보관하는 정책 전환(처리방침·R2 수명 주기)은 S15P31E102-375에서 합니다. [계약](../docs/specs/practice-records.md), [결정·검증](../docs/plans/S15P31E102-342.md).

마이페이지는 `GET /api/users/me/practice-records/recent?weekOf=YYYY-MM-DD`로 본인의 최신 저장 기록 한 건과 해당 KST 주의 집계를 받습니다. [REC-05 계약](../docs/specs/practice-records.md#rec-05-get-apiusersmepractice-recordsrecent).

### 공개 곡 조회

`GET /api/songs`와 `GET /api/songs/{songId}`는 게시·미삭제 곡만 제공합니다. 숫자 ID는 문자열이고 원본 공통 악보 JSON과 악보 버전·장단을 그대로 반환합니다. [계약](../docs/specs/practice-read-apis.md), [검증](../docs/plans/S15P31E102-256.md)을 따릅니다.

### 카카오 서명형 연결 해제 웹훅

`POST /api/webhooks/kakao/events`는 `application/secevent+jwt` 원문 SET(최대16KiB)을 받습니다. 사용자 쿠키와 분리하며 카카오 RS256 공개키·앱·대상·주체·User Unlinked 이벤트를 검증합니다. 검증 후 카카오 연결만 삭제하고 회원·기록·다른 소셜 연결·기존 세션을 보존합니다. 빈202, 검증 실패400의 `err`/`description`, 일시 장애503은 공통 API 형식의 예외입니다. Swagger에도 이 계약을 표시합니다.

`AUTH_KAKAO_WEBHOOK_APP_ID`에 해당 카카오 앱 ID를, 기존 `AUTH_OAUTH_KAKAO_CLIENT_ID`에는 대표 REST API 키를 주입합니다. 둘 중 하나라도 없으면 기동은 가능하나 웹훅은503입니다. 어드민 키는 사용하지 않습니다. 실제 값은 서버 환경에서 관리하고 Git·로그에 남기지 않습니다.

배포 전에 백업과 V9 적용 상태를 확인하고 기존 마이그레이션 절차로 **V10**을 적용해야 합니다. V10은 선택적인 OAuth 시작 시각 열과 이벤트 해시 영수증·subject 해시 워터마크를 추가합니다. 새 JAR는 이 스키마가 필요합니다. 되돌릴 때는 콘솔 송신을 먼저 끄고 이전 JAR를 배포하며 추가 열·테이블은 보존합니다. 자동 다운 마이그레이션이나 데이터 삭제는 하지 않습니다.

배포·설정 주입·수신 확인 후 별도 승인으로 카카오 콘솔에 `https://eolssu.site/api/webhooks/kakao/events`를 등록하고 **계정 상태 변경 → User Unlinked만** 구독합니다. 기존 어드민 키 방식의 연결 해제 콜백을 함께 활성화하지 않습니다. 실제 계정의 연결 해제·중복 전달·세션 보존을 확인하고, 지속503은 설정/JWKS 접근/DB 잠금을 점검합니다. 공식 재시도는 최초 포함6회이며 연속3시간 실패 시 비활성화될 수 있습니다. 현재 로컬 구현·검증 단계이며 콘솔 활성화와 실제 송신 검증은 아직 하지 않았습니다. [상세 계약·보관·시간 경계](../docs/specs/kakao-webhook.md), [진행 기록](../docs/plans/S15P31E102-296.md).

### 계정 한정 인증 만료 예외

`AUTH_NON_EXPIRING_USER_IDS`에 지정한 회원 ID만 서버 토큰의 시간 만료를 없앱니다(기본 빈 목록). access는 exp 없이 서명하지만 DB의 활성 refresh 세션과 ACTIVE 회원 검증을 매 요청 수행합니다. 로그아웃·비밀번호 변경·탈퇴·회전·예외 해제 후에는 기존 토큰을 거부합니다. 일반 계정은 access 30분·refresh 14일을 유지합니다. 예외 계정의 재발급 응답 accessTokenExpiresAt은 null이며 쿠키는 브라우저 제한을 고려해 400일입니다. 배포·검증 순서는 [Task 260](../docs/plans/S15P31E102-260.md)을 따릅니다. V7은 기존 데이터의 시각을 바꾸지 않고 refresh_tokens.expires_at을 nullable로 확장합니다.

### 일반·QA 앱의 App Links 지문

기존 `AUTH_ANDROID_APP_SHA256_FINGERPRINTS`는 일반 패키지 `com.ssafy.eolssu` 전용으로 유지합니다. `AUTH_ANDROID_QA_APP_SHA256_FINGERPRINTS`는 `com.ssafy.eolssu.qa` 전용이며 각 실제 설치 APK의 공개 SHA-256 인증서 지문을 쉼표로 설정합니다. 한 패키지 지문을 다른 패키지에 복사하지 않습니다. 설정된 패키지만 JSON 배열에 포함하고 둘 다 비어 있을 때만404입니다. 두 설정 모두 형식 검증·대문자 콜론 표기 정규화·중복 제거를 적용합니다. 설정은 운영 Compose 전달과 백엔드 반영 후 확인합니다.

검증 순서: 설치 APK 패키지·versionCode·공개 서명 확인 → `/.well-known/assetlinks.json`의 리다이렉트 없는200/application/json과 패키지별 지문 일치 → 기기 `adb shell pm verify-app-links --re-verify com.ssafy.eolssu.qa` 후 `adb shell pm get-app-links com.ssafy.eolssu.qa`에서 도메인 verified → 새 카카오 인증 시작부터 앱 복귀·완료 API를 확인합니다. 재검증은 비동기이며 강제 인텐트 실행만으로 verified를 대체하지 않습니다. 키스토어·비밀번호는 공유하지 않습니다.

### 소셜 신규 가입·재인증 탈퇴

[OAuth 가입 계약](../docs/specs/social-sign-up.md)을 따릅니다. V12 적용이 필요하며 운영 마이그레이션은 배포 전에 별도 절차로 수행합니다. 신규 인증은409 SOCIAL_SIGN_UP_REQUIRED의10분·1회 signupTicket을 반환하며 POST /api/auth/oauth/sign-up에서 현재 약관·닉네임으로 가입을 완료합니다. 비밀번호는 저장하지 않고 확인되지 않은 이메일은NULL입니다. GET /api/users/me.email도nullable입니다. 소셜 탈퇴는 연결 제공자 WITHDRAW 재인증 증명으로 기존 DELETE /api/users/me를 호출하며, 기존 비밀번호 방식도 유지합니다. FE는 신규 약관 화면·nullable 이메일·탈퇴 복귀 흐름을 연결해야 합니다. 원문 티켓·코드·쿠키를 로그에 기록하거나 자동 재전송하지 마세요.

### 악보 작성 지원음

`GET /api/instruments/{instrumentId}/authoring-capabilities`는 등록 운지·화면 표기·CAT 합성의 교집합과 제외 사유를 반환합니다. 문헌상 물리 음역과 실물 연주 검수 상태는 별도이며 운지 미등록은 `UNKNOWN`입니다. 기존 CAT-06 음역 조회와 과거 악보 읽기는 유지합니다. 최종 저장(SHT-04)과 생성(SHT-12)에 같은 정책을 적용하고 초안(SHT-11)은 미지원 음도 보존합니다. `INVALID_REQUEST.details`의 `UNSUPPORTED_PITCHES`에는 악기 코드, eventId, cellId와 reasonCodes가 있습니다. 음원 악기를 생략하면 저장 악기의 코드를 사용합니다.

정책 원본은 `data/src/playback/profiles/authoring-policy.json`·악기 profile과 표기 계약 `data/src/scores/score_data.schema.json`이며 `processResources`가 직접 묶습니다. [지원음 API·검증 계획](../docs/plans/S15P31E102-787.md)을 따릅니다. 검증은 `BuiltinFingeringApiDatabaseTests`의 실제 98운지 카탈로그 HTTP, `SheetAudioDatabaseTests`의 저장·초안·음원 HTTP를 사용합니다.

S15P31E102-789의 장단 칸 음가는 `data/reference/jangdans.json`을 같은 방식으로 `catalog/jangdans.json`에 묶습니다. 저장소 루트 Docker context의 allowlist도 이 원본을 포함합니다. `SheetJangdanValidationTests`, `UserSheetApiDatabaseTests`, `CatalogReferenceApiDatabaseTests`가0tick·첫 쉼·완전 주기와 카탈로그 응답을 검증합니다. [장단 분할 검증 기록](../docs/plans/S15P31E102-789.md).

### 구간 빠르기 보존과 생성 사전검사

S15P31E102-790은 SHT-04/08/11의 선택적 `expressions`와 V43 저장을 연결합니다. 생략/null은 현재 초안 계획 우선으로 보존하고 기본 BPM 변경은 구간 비율을 한 번만 적용합니다. 새 확정 저장은 `SHEET_AUDIO_BASE_URL`의 실제 생성기 사전검사를 사용하며, 미설정/장애는 503으로 판본을 보존합니다. 숫자 계획의 구조 위치 변경은 `TEMPO_UNRENDERABLE/PLAN_POSITIONS_CHANGED`, 짧은 음은 실제 MIDI/PCM 조건으로 거절합니다. 기존 읽기와 초안은 일괄 변경하지 않습니다.

폐기 DB의 `TEST_DB_*`와 실제 생성기 `TEST_PLAYBACK_URL`을 지정하여 `databaseTest --tests '*SheetTempoApiDatabaseTests' --tests '*SheetAuthoringCatDatabaseTests'`를 실행합니다. 단소 CAT 자산·장구6샘플·FFmpeg가 필요하며 객체 저장소만 대역입니다. [790 계획·실제 검증](../docs/plans/S15P31E102-790.md).
