# 구현된 업무 API Swagger 문서화

- 작업: S15P31E102-253. 사용자 요청(2026-10-29), 인프라·운영 Epic21 직접 연결(Story 미지정).
- 대상: 210과 최신 develop의 분석 재시도를 포함한 현재 업무 API20개. 미구현 공개 보안 경로·Actuator는 제외한다.
- 2026-10-29 사용자 결정으로 문서 조회의 로그인 요건을 폐기한다. 배포 서버 https://eolssu.site 에서도 GET /api/swagger-ui.html, /api/swagger-ui/**, /api/v3/api-docs 및 /api/v3/api-docs/**는 인증 없이 제공한다. 없거나 잘못된·만료된 쿠키도 조회를 막지 않는다. 기존 /api 프록시를 사용하며 운영 서버 배포 실행은 범위 밖이다.
- OpenAPI는 실제 컨트롤러의 요청 DTO/파라미터와 응답 결과 DTO를 바탕으로 생성한다. HTTP 상태·공통 envelope·result.errorCode·선택 details·204 빈 본문·multipart audio·Idempotency-Key·access/refresh 쿠키 인증을 명시한다.
- 성공/오류 JSON과 인증 정책은 유지한다. 문서가 새 업무 API나 운영 데이터를 생성하지 않는다. 샘플 비밀번호·토큰은 가짜 값만 사용하며 실제 비밀값은 문서에 담지 않는다.
- Swagger UI의 쿠키 보안 정의는 브라우저에서 HttpOnly 쿠키를 직접 설정할 수 없으므로, 같은 출처의 앱/로그인 API로 먼저 로그인한 뒤 브라우저가 쿠키를 전송한다. Bearer 헤더 인증을 새로 열지 않는다.
- 문서 공개는 API 구조와 가짜 예시의 조회만 허용한다. 실제 회원 API의 인증과 ACTIVE 회원 검증은 유지한다. Try it out으로 회원 API를 호출하려면 같은 출처에서 로그인해야 한다.
- 완료: 실제 HTTP OpenAPI/UI/정적 자산의 무인증 조회·잘못된 쿠키 조회와 회원 API의 무인증 거절, 전체 매핑 일치·스키마·대표 오류/인증/204/업로드를 검사하고, clean build 및 필요한 databaseTest 회귀를 통과한다. 업무 코드에 영향이 있는 변경은 테스트 먼저 수행한다.
