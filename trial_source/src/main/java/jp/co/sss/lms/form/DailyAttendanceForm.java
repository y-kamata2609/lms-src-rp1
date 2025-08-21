package jp.co.sss.lms.form;

import lombok.Data;

/**
 * 日次の勤怠フォーム
 * 
 * @author 東京ITスクール、鎌田優樹
 */
@Data
public class DailyAttendanceForm {

	/** 受講生勤怠ID */
	private Integer studentAttendanceId;
	/** 途中退校日 */
	private String leaveDate;
	/** 日付 */
	private String trainingDate;
	/** 出勤時間 */
	private String trainingStartTime;
	/** 退勤時間 */
	private String trainingEndTime;
	/** 中抜け時間 */
	private Integer blankTime;
	/** 中抜け時間（画面表示用） */
	private String blankTimeValue;
	/** ステータス */
	private String status;
	/** 備考 */
	private String note;
	/** セクション名 */
	private String sectionName;
	/** 当日フラグ */
	private Boolean isToday;
	/** エラーフラグ */
	private Boolean isError;
	/** 日付（画面表示用） */
	private String dispTrainingDate;
	/** ステータス（画面表示用） */
	private String statusDispName;
	/** LMSユーザーID */
	private String lmsUserId;
	/** ユーザー名 */
	private String userName;
	/** コース名 */
	private String courseName;
	/** インデックス */
	private String index;
	//task26追加分
	/** 研修開始時刻-時 */
	private String trainingStartTimeHour;
	/** 研修開始時刻-分 */
	private String trainingStartTimeMinute;
	/** 研修終了時刻-時 */
	private String trainingEndTimeHour;
	/** 研修終了時刻-分 */
	private String trainingEndTimeMinute;

	/**
	 * trainingStartTimeから時と分を分割して設定
	 * @return なし
	 */
	public void splitTrainingStartTime() {
		// 初期化
		this.trainingStartTimeHour = "";
		this.trainingStartTimeMinute = "";

		if (trainingStartTime != null && !trainingStartTime.trim().isEmpty()) {
			String[] parts = trainingStartTime.split(":");
			if (parts.length >= 2) {
				this.trainingStartTimeHour = parts[0].trim();
				this.trainingStartTimeMinute = parts[1].trim();
			}
		}
	}

	/**
	 * trainingEndTimeから時と分を分割して設定
	 * @return なし
	 */
	public void splitTrainingEndTime() {
		// 初期化
		this.trainingEndTimeHour = "";
		this.trainingEndTimeMinute = "";

		if (trainingEndTime != null && !trainingEndTime.trim().isEmpty()) {
			String[] parts = trainingEndTime.split(":");
			if (parts.length >= 2) {
				this.trainingEndTimeHour = parts[0].trim();
				this.trainingEndTimeMinute = parts[1].trim();
			}
		}
	}

	/**
	 * 時と分からtrainingStartTimeを組み立て
	 * @return なし
	 */
	public void combineTrainingStartTime() {
		if (trainingStartTimeHour != null && !trainingStartTimeHour.isEmpty() &&
				trainingStartTimeMinute != null && !trainingStartTimeMinute.isEmpty()) {
			this.trainingStartTime = trainingStartTimeHour + ":" + trainingStartTimeMinute;
		}
	}

	/**
	 * 時と分からtrainingEndTimeを組み立て
	 * @return なし
	 */
	public void combineTrainingEndTime() {
		if (trainingEndTimeHour != null && !trainingEndTimeHour.isEmpty() &&
				trainingEndTimeMinute != null && !trainingEndTimeMinute.isEmpty()) {
			this.trainingEndTime = trainingEndTimeHour + ":" + trainingEndTimeMinute;
		}
	}

}
