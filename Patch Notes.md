Patch Notes(지시사항-작업내용 순)

- 지시사항: availableDates 수집 후 비어 있으면 "예약 가능한 날짜가 없습니다." 메시지를 로그와 진행상태로 전송하여 MainActivity에서 표시되도록 할 것.
- 작업방향 수정내용: 해당 없음.
- 작업내용: ReservationWorker.kt에서 availableDates가 비어 있을 때 위 메시지를 로그와 진행상태로 전송하도록 로직 추가.
