
// 예약 가능 여부를 확인하고 결과를 Android 측에 전달하는 스크립트
(function() {
    const months = '__TARGET_MONTHS__'.split(',').map(m => m.trim());
    let availableDates = [];
    let errorMsg = null;

    async function checkMonth(month) {
        const [year, monthStr] = month.split('-');
        const nextDt = year + monthStr.padStart(2, '0') + '01';
        const url = '/reservation/medDateListAjax.do?hsp_cd=1&dept_cd=OSHS&dr_cd=05081&nextDt=' + nextDt;

        try {
            const response = await fetch(url, {
                headers: {
                    'X-Requested-With': 'XMLHttpRequest'
                }
            });

            // 로그인 세션이 만료되면 JSON이 아닌 HTML 페이지가 반환되므로 이를 체크
            const contentType = response.headers.get('content-type') || '';
            if (!contentType.includes('application/json')) {
                throw new Error('LOGIN_REQUIRED');
            }

            const data = await response.json();
            if (data.scheduleList) {
                data.scheduleList.forEach(schedule => {
                    if (schedule.meddate) {
                        availableDates.push(schedule.meddate);
                    }
                });
            }
        } catch (e) {
            errorMsg = e.message || 'UNKNOWN_ERROR';
            return false; // 중단하여 빠르게 결과 전달
        }
        return true;
    }

    async function run() {
        for (const month of months) {
            const ok = await checkMonth(month);
            if (!ok) break; // 오류 발생 시 반복 중단
            await new Promise(resolve => setTimeout(resolve, 1000)); // 1초 대기
        }
        Android.processResult(JSON.stringify({ dates: availableDates, error: errorMsg }));
    }

    run();
})();
