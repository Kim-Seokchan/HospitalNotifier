
(function() {
    const months = '__TARGET_MONTHS__'.split(',').map(m => m.trim());
    let availableDates = [];

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
            const data = await response.json();
            if (data.scheduleList) {
                data.scheduleList.forEach(schedule => {
                    if (schedule.meddate) {
                        availableDates.push(schedule.meddate);
                    }
                });
            }
        } catch (e) {
            console.error('Error checking month:', e);
        }
    }

    async function run() {
        for (const month of months) {
            await checkMonth(month);
            await new Promise(resolve => setTimeout(resolve, 1000)); // 1초 대기
        }
        Android.processResult(JSON.stringify(availableDates));
    }

    run();
})();
