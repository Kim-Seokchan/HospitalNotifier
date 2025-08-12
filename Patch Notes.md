Patch Notes(지시사항-작업내용 순, 신규사항은 기존의 아래 쪽에 추가 기재할 것)

- 지시사항: availableDates 수집 후 비어 있으면 "예약 가능한 날짜가 없습니다." 메시지를 로그와 진행상태로 전송하여 MainActivity에서 표시되도록 할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 availableDates가 비어 있을 때 위 메시지를 로그와 진행상태로 전송하도록 로직 추가.

- 지시사항: YYYY-MM 형식의 년-월 입력을 정규식 등으로 검증하고, 잘못된 입력은 저장 전에 경고 및 거부하며 필요 시 공백 제거 및 월 자리수 보정을 할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: MainActivity.kt에서 targetMonths 입력을 검증·정규화하고, 잘못된 경우 토스트로 알린 후 저장을 중단하도록 수정.

- 지시사항: MockWebServer를 사용하여 loginProc.do를 모킹하고 JSESSIONID1 쿠키 저장을 검증할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: MockWebServer 기반 단위 테스트와 의존성을 추가하여 실제 네트워크 호출 없이 쿠키 지속성을 확인하도록 변경.

- 지시사항: 예약 조회 실패 시 오류 로그 및 진행상태 메시지를 전송하여 UI에서 진단 정보를 표시하도록 할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt의 예약 조회 예외 처리에 Log.e와 setProgress를 추가하여 오류 메시지를 기록하고 전송하도록 수정.

- 지시사항: baseClient에 네트워크 타임아웃을 설정하고 TimeUnit을 임포트할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ApiClient.kt의 baseClient에 connectTimeout 10초, readTimeout 10초, callTimeout 20초를 추가하고 java.util.concurrent.TimeUnit을 임포트함.

- 지시사항: checkAvailability 응답에서 401/302 외의 비성공 HTTP 코드를 처리하고, 상태코드·에러본문을 로그와 진행상태로 전송하며, 서버 오류는 재시도, 기타는 실패로 처리할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에 비성공 응답 처리 분기를 추가해 HTTP 상태코드와 errorBody를 기록하고 진행상태로 전송하며, 5xx는 Result.retry(), 그 외는 Result.failure()를 반환하도록 수정.

- 지시사항: 로그인 후 세션 쿠키(JSESSIONID1) 확보 여부를 확인하고, 없으면 오류 로그·진행상태 갱신 후 실패를 반환하며, 성공 시 쿠키 이름/값을 로그로 남길 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.startLoginProcess에서 로그인 후 SharedPreferences의 JSESSIONID1 쿠키를 검사하는 로직을 추가하고, 미존재 시 setProgress와 함께 Result.failure()를 반환하도록 수정. 쿠키 존재 시 이름/값을 로그로 출력하며, MyCookieJarTest에 성공/실패 시나리오를 검증하는 테스트와 MockK 및 WorkManager 테스트 의존성을 추가.

- 지시사항: 예약 조회 워커를 시작하기 전에 기존 워크를 취소하고, 중복 실행을 방지하도록 고유 워크로 등록할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: MainActivity.startWork에서 cancelAllWorkByTag 후 enqueueUniqueWork와 enqueueUniquePeriodicWork를 사용하도록 수정하여 "reservationWork" 태그의 워커가 하나만 실행되도록 함.

- 지시사항: LiveData에서 RUNNING 상태인 최신 WorkRequest만 추적하고 완료된 WorkInfo의 progress는 중복 기록되지 않도록 처리하며 stopWork 호출 시 관찰 상태를 초기화할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: MainActivity.observeWorker에서 RUNNING 상태 WorkInfo 중 가장 최근 항목만 선택하고 완료된 WorkInfo는 집합으로 관리해 progress 중복 로그를 방지하도록 수정하고, stopWork에서 추적 ID와 집합을 초기화함.

- 지시사항: ReservationWorker.startLoginProcess 실행 직전에 cookies SharedPreferences를 clear()하여 기존 세션을 제거하고, 로그인 후 필수 세션 쿠키를 확인하여 없으면 "세션 쿠키 없음" 상태 로그와 함께 실패를 반환하며, 실패 시 cookies와 로그인 정보 저장소에서 관련 항목을 삭제할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 startLoginProcess 호출 전 clearCookies()를 수행하고, 로그인 후 JSESSIONID1 검증 실패 시 상태 로그를 남기고 cookies 및 settings에서 쿠키와 로그인 정보를 제거하도록 수정. 실패 테스트에 이러한 정리 동작을 확인하는 단위 테스트 보강.

- 지시사항: MainActivity.startWork에서 즉시 실행이 필요하지 않으면 원타임 WorkRequest를 제거하고 주기 작업만 사용하며, stopWork에서 현재 실행 중인 작업을 cancelAllWorkByTag(WORK_TAG)로 중단할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: MainActivity.kt의 startWork를 enqueueUniquePeriodicWork만 사용하도록 정리하고, stopWork에서 cancelAllWorkByTag를 호출하여 실행 중인 작업을 즉시 취소하도록 변경.

- 지시사항: startLoginProcess에서 세션 쿠키 검색을 JSESSIONID 접두사 전체로 일반화하고, 쿠키를 찾지 못한 경우에만 실패로 처리할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 JSESSIONID1 대신 JSESSIONID 접두사를 검색하도록 수정하고, MyCookieJarTest.kt의 성공 시나리오에서 JSESSIONID2 쿠키로 검증하도록 변경.

- 지시사항: startLoginProcess에서 clearLoginInfo() 호출을 제거하거나 조건부로 실행하고, 실패해도 settings에 저장된 ID/비밀번호가 유지되도록 할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 세션 쿠키 미확보나 로그인 예외 시 clearLoginInfo() 호출을 제거해 로그인 정보를 유지하고, 사용자 입력이 잘못된 경우에만 clearLoginInfo()가 실행되도록 수정함.

- 지시사항: doWork에서 ID, 비밀번호, 조회 월이 없을 때 각각 진행상태에 원인을 기록하고 실패를 반환하며, startLoginProcess에서 로그인 실패(response에 login.do 포함 또는 예외 발생 시)에도 "로그인 실패:" 메시지를 진행상태에 기록할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 각 null 항목에 대한 setProgress와 실패 처리를 추가하고, 로그인 실패 상황에서도 setProgress로 원인을 전달하도록 수정함.

- 지시사항: LiveData에서 RUNNING 뿐 아니라 FAILED, SUCCEEDED 등의 마지막 상태 WorkInfo도 확인하여 로그에 상태 메시지를 표시할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: MainActivity.observeWorker에서 완료된 WorkInfo 중 관찰되지 않은 항목의 progress나 outputData에 있는 status 메시지를 출력하고 ID를 집합에 추가하도록 수정하여 즉시 실패한 작업의 마지막 메시지도 표시되도록 함.

- 지시사항: 워커가 즉시 종료되는 경우에도 마지막 상태 메시지를 UI에서 확인할 수 있도록, 모든 조기 실패에서 상태를 outputData로 반환하고 성공 시에도 최종 메시지를 outputData에 포함할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 ID·비밀번호·조회 월 누락, 로그인 실패, 세션 쿠키 없음, 예약 조회 실패 등의 모든 Result.failure에 workDataOf("status" ...)를 제공하고, 조회 성공 시 마지막 메시지를 담은 Result.success(workDataOf("status" ...))를 반환하도록 변경.

- 지시사항: startLoginProcess에서 clearLoginInfo() 호출을 제거하거나 조건부로 실행하고, 실패해도 settings에 저장된 ID/비밀번호가 유지되도록 할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 로그인 실패 시 clearLoginInfo() 호출을 삭제해 SharedPreferences에 ID/비밀번호가 남아 다음 예약 조회 주기에 재로그인을 시도할 수 있도록 수정함.

- 지시사항: 로그인 후 cookiesPref.all에서 JSESSIONID를 포함하고 비어 있지 않은 쿠키가 하나 이상 있는지 확인하고, 없으면 "세션 쿠키 없음" 상태를 전송 후 실패를 반환할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt의 startLoginProcess에서 JSESSIONID가 포함된 값이 비어 있지 않은 쿠키를 검사하도록 변경하고, 존재하지 않을 경우 setProgress("세션 쿠키 없음")과 함께 Result.failure(workDataOf("status" to "세션 쿠키 없음"))을 반환하여 check_snuh.py와 같이 쿠키 확보 실패 시 처리를 중단하도록 함.

- 지시사항: 로그인 이후 예약조회가 제대로 되지 않는다. 실행시 "세션 쿠키 없음" 이후 진행되지 않는다.
- 작업방향 수정내용: 해당 없음.
- 작업내용: MyCookieJar.kt의 SharedPreferences 기반 쿠키 저장 로직을 인메모리 방식으로 변경하여 쿠키 관리의 복잡성을 줄이고, ReservationWorker.kt에서 SharedPreferences 대신 ApiClient를 통해 쿠키를 직접 관리하도록 수정하여 세션 쿠키 문제를 해결함. ApiClient.kt에 OkHttpClient 인스턴스를 외부에 제공하는 getOkHttpClient() 메소드를 추가함.

- 지시사항: "세션 쿠키 없음" 오류가 계속 발생한다.
- 작업방향 수정내용: 쿠키의 도메인 불일치 가능성을 해결하기 위해 MyCookieJar의 쿠키 검색 로직을 개선.
- 작업내용: MyCookieJar.kt의 loadForRequest 메소드가 특정 호스트가 아닌 모든 저장된 쿠키를 대상으로 cookie.matches(url)를 사용하여 검사하도록 수정함. getCookies 메소드는 전체 URL을 인자로 받아 loadForRequest를 호출하도록 변경함. ReservationWorker.kt에서는 getCookies를 호출할 때 전체 URL(https://www.snuh.org/)을 사용하도록 수정함.

- 지시사항: "세션 쿠키 없음" 오류가 계속 발생하여, 문제의 원인을 파악하기 위해 네트워크 요청/응답의 전체 내용을 로그로 확인할 필요가 있다.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ApiClient.kt에서 HttpLoggingInterceptor의 레벨을 HEADERS에서 BODY로 변경하여, 네트워크 통신의 모든 내용을 로그로 출력하도록 함.

- 지시사항: "세션 쿠키 없음" 오류가 계속 발생한다.
- 작업방향 수정내용: 로그인 로직을 명확히 하고 쿠키 확인을 개선하기 위해 코드 구조를 변경.
- 작업내용: ReservationWorker.kt에서 세션 쿠키를 찾기 위해 `it.name == "JSESSIONID"` 대신 `it.name.startsWith("JSESSIONID")`를 사용하도록 수정하여 `JSESSIONID1`과 같은 변형된 쿠키 이름도 감지할 수 있도록 함. MainActivity.kt에서 중복된 로그인 로직을 제거하고, 사용자 정보를 저장한 뒤 바로 ReservationWorker를 시작하도록 하여 전체적인 흐름을 단순화하고 디버깅을 용이하게 함.

- 지시사항: 예약 가능일이 없는 달을 조회하면, 다음 예약 가능일이 있는 달의 결과가 대신 표시된다.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 서버로부터 받은 예약 가능 날짜 목록을 처리할 때, 해당 날짜가 현재 조회 중인 연도와 월에 속하는지 확인하는 검증 로직을 추가함. 이를 통해 요청하지 않은 월의 예약 날짜가 결과에 포함되는 문제를 해결함.

- 지시사항: 예약 가능한 날짜가 있을 때 텔레그램 메시지가 전송되도록 한다.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 텔레그램 메시지를 보내는 로직에 상세한 오류 로깅을 추가함. 메시지 전송 API 호출의 성공 여부를 확인하고, 실패 시 HTTP 상태 코드와 오류 내용을 로그에 기록하여 텔레그램 연동 문제를 진단하기 용이하도록 개선함. 또한, 메시지에 포함되는 날짜를 YYYYMMDD 형식에서 YYYY-MM-DD 형식으로 변경하여 가독성을 높임.

- 지시사항: 텔레그램 전송 로그가 표시되지 않는다.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 예약 가능한 날짜가 있지만 텔레그램 토큰 또는 Chat ID가 제공되지 않은 경우, 이를 알리는 경고 로그를 추가하여 메시지가 전송되지 않은 이유를 명확히 함.

- 지시사항: 텔레그램 전송이 안되고 로그에 관련 내용이 표시되지 않는다.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 텔레그램 API 호출 시 `bot` 접두사가 중복으로 들어가는 오류를 수정함. 또한, 메시지 전송 직전에 토큰과 채팅 ID를 마스킹하여 로그에 기록하도록 하여, 텔레그램 정보가 정상적으로 인식되고 있는지 확인할 수 있도록 함.

- 지시사항: 중지 버튼을 누르면 모든 데이터, 프로세스가 초기화되도록 하시오. 이를 통해 예약조회시작을 다시 누를 때 클린한 로직이 구현될 수 있도록 한다.
- 작업방향 수정내용: 해당 없음.
- 작업내용: MainActivity.kt의 stopWork() 함수를 수정하여 WorkManager 작업을 취소하는 것 외에 SharedPreferences, UI 입력 필드, 로그, 쿠키를 지우고 스피너를 초기 상태로 재설정하도록 함.

- 지시사항: 텔레그램 정보를 넣지 않았을때 출력되는 로그가 가끔씩 출력되지 않는다.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 예약 가능한 날짜가 있지만 텔레그램 정보가 없는 경우, 경고 메시지를 별도의 진행률 업데이트로 설정하는 대신 최종 결과 메시지(finalMessage)에 추가하도록 수정했습니다. 이렇게 하면 최종 작업자 상태 업데이트에 의해 메시지가 덮어쓰이는 것을 방지하여 항상 로그에 표시되도록 합니다.

- 지시사항: 가끔씩 Available dates 로그가 두번씩 출력된다. 왜 그런가?
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 Result.success를 반환하기 직전에 수행하던 중복된 Log.d 및 setProgress 호출을 제거했습니다. 이로써 최종 상태 메시지가 MainActivity에서 진행률 업데이트와 최종 결과로 두 번 기록되어 중복 로그가 발생하는 문제를 해결했습니다.

- 지시사항: 텔레그램 정보가 없어 메세지를 보내지 않았다는 로그 처럼, 텔레그램 전송을 했다는 로그도 출력하도록 하시오.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 텔레그램 메시지가 성공적으로 전송되면 확인 메시지가 finalMessage에 추가됩니다. 성공 메시지에 대한 별도의 setProgress 호출은 최종 작업자 상태 업데이트에 의해 덮어쓰이는 것을 방지하기 위해 제거되었습니다. 이렇게 하면 "텔레그램 메시지 전송됨" 확인이 UI 로그에 안정적으로 표시됩니다.

- 지시사항: 조회 주기를 최소 15분이 아니라 1분 단위까지 가능하도록 하려한다. workmanager api가 15분 주기가 최소라던데 수정이 가능한가? 다른방식은 없을까?
- 작업방향 수정내용: WorkManager의 15분 최소 주기 제약을 우회하기 위해 ForegroundService를 사용하는 방식으로 변경.
- 작업내용:
  - WorkManager 기반의 ReservationWorker를 제거하고, ForegroundService 기반의 ReservationService를 새로 구현하여 1분 단위의 주기적인 작업 실행이 가능하도록 수정.
  - MainActivity에서 WorkManager 관련 코드를 제거하고 ReservationService를 시작/중지하고 서비스로부터 로그를 수신하여 UI에 표시하도록 변경.
  - AndroidManifest.xml에 FOREGROUND_SERVICE 권한을 추가하고 ReservationService를 등록.
  - 조회 주기 선택 UI에 1, 5, 10분 옵션을 추가.

## 2025-08-12

**작업 지시사항:**
- 포그라운드 서비스로 전환 후 발생하는 앱 충돌 및 알림 미표시 문제 해결

**작업 방향 수정:**
- (해당 없음)

**작업 내용:**
- **버그 수정:** 
    - `AndroidManifest.xml`의 `ReservationService`에 `android:foregroundServiceType="dataSync"` 속성을 추가하여 Android 14 이상에서 발생하는 `MissingForegroundServiceTypeException` 오류를 해결했습니다.
    - Android 13 이상에서 포그라운드 서비스 알림이 표시되지 않는 문제를 해결하기 위해 `POST_NOTIFICATIONS` 권한을 요청하는 로직을 추가했습니다. `AndroidManifest.xml`에 권한을 선언하고, `MainActivity`에서 앱 시작 시 사용자에게 권한을 요청하도록 수정했습니다.