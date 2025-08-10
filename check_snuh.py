import time
import json
from datetime import datetime
import requests
import getpass

# Selenium 관련 라이브러리
from selenium import webdriver
from selenium.webdriver.common.by import By
from selenium.webdriver.chrome.service import Service
from selenium.webdriver.support.ui import WebDriverWait
from selenium.webdriver.support import expected_conditions as EC
from webdriver_manager.chrome import ChromeDriverManager

# --- 💧 사용자 설정 영역 💧 ---

# 텔레그램 설정 (★★ 여기에 1, 2단계에서 얻은 정보를 입력하세요 ★★)
TELEGRAM_BOT_TOKEN = ""  # 1단계에서 받은 봇 토큰
TELEGRAM_CHAT_ID = ""    # 2단계에서 확인한 나의 챗 ID

# 예약 확인할 의사 정보
HSP_CD = '1'
DEPT_CD = 'OSHS'
DR_CD = '05081'
# -----------------------------

def send_telegram_message(message):
    """텔레그램 봇을 통해 메시지를 발송합니다."""
    if TELEGRAM_BOT_TOKEN == "" or TELEGRAM_CHAT_ID == "":
        print("💡 텔레그램 토큰 또는 챗 ID가 설정되지 않아 텔레그램 메시지를 발송하지 않습니다.")
        return

    url = f"https://api.telegram.org/bot{TELEGRAM_BOT_TOKEN}/sendMessage"
    payload = {
        'chat_id': TELEGRAM_CHAT_ID,
        'text': message,
        'parse_mode': 'Markdown' # 메시지 텍스트를 꾸미기 위한 옵션
    }
    try:
        response = requests.post(url, data=payload)
        if response.status_code == 200:
            print("✅ 텔레그램 메시지 발송 성공!")
        else:
            print(f"❌ 텔레그램 메시지 발송 실패: {response.text}")
    except Exception as e:
        print(f"❌ 텔레그램 발송 중 네트워크 오류 발생: {e}")

def get_session_cookie(user_id, user_pw):
    """Selenium으로 로그인하여 세션 쿠키를 가져옵니다."""
    print("🤖 Selenium으로 자동 로그인을 시작합니다...")
    try:
        options = webdriver.ChromeOptions()
        options.add_argument('headless')
        options.add_argument('window-size=1920x1080')
        options.add_argument("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36")
        service = Service(ChromeDriverManager().install())
        driver = webdriver.Chrome(service=service, options=options)
        driver.get("https://www.snuh.org/login.do")
        driver.find_element(By.ID, "id").send_keys(user_id)
        driver.find_element(By.ID, "pass").send_keys(user_pw)
        driver.find_element(By.ID, "loginBtn").click()
        WebDriverWait(driver, 10).until(EC.presence_of_element_located((By.LINK_TEXT, "로그아웃")))
        print("✅ 로그인에 성공했습니다.")
        cookies = driver.get_cookies()
        cookie_string = "; ".join([f"{cookie['name']}={cookie['value']}" for cookie in cookies])
        return cookie_string
    except Exception:
        return None
    finally:
        if 'driver' in locals():
            driver.quit()

def check_reservation(session_cookie, target_year, target_months):
    """예약 가능 여부를 확인하고, 결과에 따라 알림을 보냅니다."""
    if not session_cookie: return False
    print(f"\n🚀 [{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}] 예약 확인 중...")
    
    HEADERS = {'Cookie': session_cookie, 'Referer': 'https://www.snuh.org/reservation/reservation.do', 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Safari/537.36', 'X-Requested-With': 'XMLHttpRequest'}
    URL = "https://www.snuh.org/reservation/medDateListAjax.do"
    all_found_dates = []
    
    for month in target_months:
        params = {'dept_cd': DEPT_CD, 'dr_cd': DR_CD, 'nextDt': f"{target_year}{month:02d}01"}
        try:
            response = requests.get(URL, params=params, headers=HEADERS)
            response.raise_for_status()
            data = response.json()
            if data.get('scheduleList'):
                for schedule in data['scheduleList']:
                    meddate_str = schedule.get('meddate')
                    if meddate_str and datetime.strptime(meddate_str, '%Y%m%d').month == month:
                        all_found_dates.append(datetime.strptime(meddate_str, '%Y%m%d').strftime('%Y년 %m월 %d일'))
        except json.JSONDecodeError:
            print("🚨 경고: 쿠키 정보가 만료되었습니다. 재로그인이 필요합니다.")
            return False
        except requests.exceptions.RequestException as e:
            print(f"  - {month}월 확인 중 네트워크 오류 발생: {e}")
            continue
        time.sleep(1)

    if all_found_dates:
        # ----> 텔레그램 알림 호출 지점 <----
        found_dates_str = "\n".join([f"  - {date_str}" for date_str in sorted(list(set(all_found_dates)))])
        console_message = f"\n==================================================\n🎉🎉🎉 예약 가능한 날짜를 찾았습니다! 🎉🎉🎉\n{found_dates_str}\n\n지금 바로 예약하세요: https://www.snuh.org/reservation/reservation.do\n=================================================="
        telegram_message = f"🎉 *김지형 교수님 예약 알림* 🎉\n\n예약 가능한 날짜가 나왔습니다!\n\n{found_dates_str}\n\n[지금 바로 예약하기](https://www.snuh.org/reservation/reservation.do)"
        
        print(console_message)
        send_telegram_message(telegram_message)
    else:
        print(f"확인 완료: 아쉽지만 조회하신 {target_year}년 {target_months}월에는 빈자리가 없습니다.")
    
    return True

# --- 메인 스크립트 실행 ---
if __name__ == '__main__':
    session_cookie = None
    my_id, my_pw = "", ""
    TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID = "", ""
    
    while not session_cookie:
        my_id = input("서울대학교병원 아이디를 입력하세요: ")
        my_pw = getpass.getpass("비밀번호를 입력하세요 (입력 시 보이지 않음): ")
        session_cookie = get_session_cookie(my_id, my_pw)
        if not session_cookie:
            print("\n❌ 로그인에 실패했습니다. 아이디와 비밀번호를 확인 후 다시 입력해주세요.\n")
    TELEGRAM_BOT_TOKEN = input("텔레그램 봇 토큰을 입력하세요: ")
    TELEGRAM_CHAT_ID = input("텔레그램 챗 ID를 입력하세요: ")

    target_year_input = input("조회할 연도를 입력하세요 (예: 2025): ")
    while not target_year_input.isdigit():
        target_year_input = input("숫자로 연도를 다시 입력하세요 (예: 2025): ")
    
    target_months_input = input("조회할 월을 입력하세요 (예: 7 8 또는 7,8): ")
    try:
        cleaned_input = target_months_input.replace(',', ' ')
        target_months = [int(month) for month in cleaned_input.split()]
    except ValueError:
        print("월 입력 형식이 잘못되었습니다. 스크립트를 다시 시작해주세요.")
        exit()
    
    # 알림 메시지 발송 후, 다시 알리지 않도록 하기 위한 상태 변수
    notified_dates = set()

    while True:
        # check_reservation 함수를 직접 수정하는 대신, 여기서 날짜를 관리
        is_successful = check_reservation(session_cookie, int(target_year_input), target_months)
        
        if not is_successful:
            print("\n🔄 쿠키가 만료되어 자동 로그인을 재시도합니다.")
            session_cookie = get_session_cookie(my_id, my_pw)
            if not session_cookie:
                print("재로그인에 실패했습니다. 10분 후 다시 시도합니다.")
                time.sleep(600)
                continue

        print(f"\n✅ 다음 확인은 5분 뒤에 진행됩니다. (Ctrl+C를 눌러 종료)")
        time.sleep(300)
