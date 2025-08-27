package jp.co.sss.lms.service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jp.co.sss.lms.dto.AttendanceManagementDto;
import jp.co.sss.lms.dto.LoginUserDto;
import jp.co.sss.lms.entity.TStudentAttendance;
import jp.co.sss.lms.enums.AttendanceStatusEnum;
import jp.co.sss.lms.form.AttendanceForm;
import jp.co.sss.lms.form.DailyAttendanceForm;
import jp.co.sss.lms.mapper.TStudentAttendanceMapper;
import jp.co.sss.lms.util.AttendanceUtil;
import jp.co.sss.lms.util.Constants;
import jp.co.sss.lms.util.DateUtil;
import jp.co.sss.lms.util.LoginUserUtil;
import jp.co.sss.lms.util.MessageUtil;
import jp.co.sss.lms.util.TrainingTime;

/**
 * 勤怠情報（受講生入力）サービス
 * 
 * @author 東京ITスクール、鎌田優樹
 */
@Service
public class StudentAttendanceService {

	@Autowired
	private DateUtil dateUtil;
	@Autowired
	private AttendanceUtil attendanceUtil;
	@Autowired
	private MessageUtil messageUtil;
	@Autowired
	private LoginUserUtil loginUserUtil;
	@Autowired
	private LoginUserDto loginUserDto;
	@Autowired
	private TStudentAttendanceMapper tStudentAttendanceMapper;

	/**
	 * 勤怠一覧情報取得
	 * 
	 * @param courseId
	 * @param lmsUserId
	 * @return 勤怠管理画面用DTOリスト
	 */
	public List<AttendanceManagementDto> getAttendanceManagement(Integer courseId,
			Integer lmsUserId) {
		// 勤怠管理リストの取得
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, Constants.DB_FLG_FALSE);
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}
		return attendanceManagementDtoList;
	}

	/**
	 * 出退勤更新前のチェック
	 * 
	 * @param attendanceType
	 * @return エラーメッセージ
	 */
	public String punchCheck(Short attendanceType) {
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 権限チェック
		if (!loginUserUtil.isStudent()) {
			return messageUtil.getMessage(Constants.VALID_KEY_AUTHORIZATION);
		}
		// 研修日チェック
		if (!attendanceUtil.isWorkDay(loginUserDto.getCourseId(), trainingDate)) {
			return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_NOTWORKDAY);
		}
		// 登録情報チェック
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		switch (attendanceType) {
		case Constants.CODE_VAL_ATWORK:
			if (tStudentAttendance != null
					&& !tStudentAttendance.getTrainingStartTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			break;
		case Constants.CODE_VAL_LEAVING:
			if (tStudentAttendance == null
					|| tStudentAttendance.getTrainingStartTime().equals("")) {
				// 出勤情報がないため退勤情報を入力出来ません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHINEMPTY);
			}
			if (!tStudentAttendance.getTrainingEndTime().equals("")) {
				// 本日の勤怠情報は既に入力されています。直接編集してください。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_PUNCHALREADYEXISTS);
			}
			TrainingTime trainingStartTime = new TrainingTime(
					tStudentAttendance.getTrainingStartTime());
			TrainingTime trainingEndTime = new TrainingTime();
			if (trainingStartTime.compareTo(trainingEndTime) > 0) {
				// 退勤時刻は出勤時刻より後でなければいけません。
				return messageUtil.getMessage(Constants.VALID_KEY_ATTENDANCE_TRAININGTIMERANGE);
			}
			break;
		}
		return null;
	}

	/**
	 * 出勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchIn() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 現在の研修時刻
		TrainingTime trainingStartTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				null);
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		if (tStudentAttendance == null) {
			// 登録処理
			tStudentAttendance = new TStudentAttendance();
			tStudentAttendance.setLmsUserId(loginUserDto.getLmsUserId());
			tStudentAttendance.setTrainingDate(trainingDate);
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setTrainingEndTime("");
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setNote("");
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setFirstCreateDate(date);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendance.setBlankTime(null);
			tStudentAttendanceMapper.insert(tStudentAttendance);
		} else {
			// 更新処理
			tStudentAttendance.setTrainingStartTime(trainingStartTime.toString());
			tStudentAttendance.setStatus(attendanceStatusEnum.code);
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			tStudentAttendanceMapper.update(tStudentAttendance);
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 退勤ボタン処理
	 * 
	 * @return 完了メッセージ
	 */
	public String setPunchOut() {
		// 当日日付
		Date date = new Date();
		// 本日の研修日
		Date trainingDate = attendanceUtil.getTrainingDate();
		// 研修日の勤怠情報取得
		TStudentAttendance tStudentAttendance = tStudentAttendanceMapper
				.findByLmsUserIdAndTrainingDate(loginUserDto.getLmsUserId(), trainingDate,
						Constants.DB_FLG_FALSE);
		// 出退勤時刻
		TrainingTime trainingStartTime = new TrainingTime(
				tStudentAttendance.getTrainingStartTime());
		TrainingTime trainingEndTime = new TrainingTime();
		// 遅刻早退ステータス
		AttendanceStatusEnum attendanceStatusEnum = attendanceUtil.getStatus(trainingStartTime,
				trainingEndTime);
		// 更新処理
		tStudentAttendance.setTrainingEndTime(trainingEndTime.toString());
		tStudentAttendance.setStatus(attendanceStatusEnum.code);
		tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
		tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
		tStudentAttendance.setLastModifiedDate(date);
		tStudentAttendanceMapper.update(tStudentAttendance);
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠フォームへ設定
	 * 
	 * @param attendanceManagementDtoList
	 * @return 勤怠編集フォーム
	 */
	public AttendanceForm setAttendanceForm(
			List<AttendanceManagementDto> attendanceManagementDtoList) {
		AttendanceForm attendanceForm = new AttendanceForm();
		attendanceForm.setAttendanceList(new ArrayList<DailyAttendanceForm>());
		attendanceForm.setLmsUserId(loginUserDto.getLmsUserId());
		attendanceForm.setUserName(loginUserDto.getUserName());
		attendanceForm.setLeaveFlg(loginUserDto.getLeaveFlg());
		attendanceForm.setBlankTimes(attendanceUtil.setBlankTime());
		// 設計書準拠のメソッド名に変更
		attendanceForm.setWorkHour(attendanceUtil.getHourMap());
		attendanceForm.setWorkMinute(attendanceUtil.getMinuteMap());
		// 途中退校している場合のみ設定
		if (loginUserDto.getLeaveDate() != null) {
			attendanceForm
					.setLeaveDate(dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy-MM-dd"));
			attendanceForm.setDispLeaveDate(
					dateUtil.dateToString(loginUserDto.getLeaveDate(), "yyyy年M月d日"));
		}
		// 勤怠管理リストの件数分、日次の勤怠フォームに移し替え
		for (AttendanceManagementDto attendanceManagementDto : attendanceManagementDtoList) {
			DailyAttendanceForm dailyAttendanceForm = new DailyAttendanceForm();
			dailyAttendanceForm
					.setStudentAttendanceId(attendanceManagementDto.getStudentAttendanceId());
			dailyAttendanceForm
					.setTrainingDate(dateUtil.toString(attendanceManagementDto.getTrainingDate()));
			dailyAttendanceForm
					.setTrainingStartTime(attendanceManagementDto.getTrainingStartTime());
			dailyAttendanceForm.setTrainingEndTime(attendanceManagementDto.getTrainingEndTime());
			if (attendanceManagementDto.getBlankTime() != null) {
				dailyAttendanceForm.setBlankTime(attendanceManagementDto.getBlankTime());
				dailyAttendanceForm.setBlankTimeValue(String.valueOf(
						attendanceUtil.calcBlankTime(attendanceManagementDto.getBlankTime())));
			}
			dailyAttendanceForm.setStatus(String.valueOf(attendanceManagementDto.getStatus()));
			dailyAttendanceForm.setNote(attendanceManagementDto.getNote());
			dailyAttendanceForm.setSectionName(attendanceManagementDto.getSectionName());
			dailyAttendanceForm.setIsToday(attendanceManagementDto.getIsToday());
			dailyAttendanceForm.setDispTrainingDate(dateUtil
					.dateToString(attendanceManagementDto.getTrainingDate(), "yyyy年M月d日(E)"));
			dailyAttendanceForm.setStatusDispName(attendanceManagementDto.getStatusDispName());
			// 設計書準拠のメソッド名に変更（戻り値Integer型）
			dailyAttendanceForm.setTrainingStartTimeHour(
					attendanceUtil.getHour(attendanceManagementDto.getTrainingStartTime()));
			dailyAttendanceForm.setTrainingStartTimeMinute(
					attendanceUtil.getMinute(attendanceManagementDto.getTrainingStartTime()));

			dailyAttendanceForm.setTrainingEndTimeHour(
					attendanceUtil.getHour(attendanceManagementDto.getTrainingEndTime()));
			dailyAttendanceForm.setTrainingEndTimeMinute(
					attendanceUtil.getMinute(attendanceManagementDto.getTrainingEndTime()));
			attendanceForm.getAttendanceList().add(dailyAttendanceForm);
		}
		return attendanceForm;
	}

	/**
	 * 勤怠登録・更新処理
	 * 
	 * @param attendanceForm
	 * @return 完了メッセージ
	 * @throws ParseException
	 */
	public String update(AttendanceForm attendanceForm) throws ParseException {

		Integer lmsUserId = loginUserUtil.isStudent() ? loginUserDto.getLmsUserId()
				: attendanceForm.getLmsUserId();

		// 現在の勤怠情報（受講生入力）リストを取得
		List<TStudentAttendance> tStudentAttendanceList = tStudentAttendanceMapper
				.findByLmsUserId(lmsUserId, Constants.DB_FLG_FALSE);

		// 入力された情報を更新用のエンティティに移し替え
		Date date = new Date();
		for (DailyAttendanceForm dailyAttendanceForm : attendanceForm.getAttendanceList()) {

			// 時分を結合（設計書外の処理だが、HTML画面との連携で必要）
			combineTrainingTime(dailyAttendanceForm);

			// 更新用エンティティ作成
			TStudentAttendance tStudentAttendance = new TStudentAttendance();
			// 日次勤怠フォームから更新用のエンティティにコピー
			BeanUtils.copyProperties(dailyAttendanceForm, tStudentAttendance);
			// 研修日付
			tStudentAttendance
					.setTrainingDate(dateUtil.parse(dailyAttendanceForm.getTrainingDate()));
			// 現在の勤怠情報リストのうち、研修日が同じものを更新用エンティティで上書き
			for (TStudentAttendance entity : tStudentAttendanceList) {
				if (entity.getTrainingDate().equals(tStudentAttendance.getTrainingDate())) {
					tStudentAttendance = entity;
					break;
				}
			}
			tStudentAttendance.setLmsUserId(lmsUserId);
			tStudentAttendance.setAccountId(loginUserDto.getAccountId());
			// 出勤時刻整形
			TrainingTime trainingStartTime = null;
			trainingStartTime = new TrainingTime(dailyAttendanceForm.getTrainingStartTime());
			tStudentAttendance.setTrainingStartTime(trainingStartTime.getFormattedString());
			// 退勤時刻整形
			TrainingTime trainingEndTime = null;
			trainingEndTime = new TrainingTime(dailyAttendanceForm.getTrainingEndTime());
			tStudentAttendance.setTrainingEndTime(trainingEndTime.getFormattedString());
			// 中抜け時間
			tStudentAttendance.setBlankTime(dailyAttendanceForm.getBlankTime());
			// 遅刻早退ステータス
			if ((trainingStartTime != null || trainingEndTime != null)
					&& !dailyAttendanceForm.getStatusDispName().equals("欠席")) {
				AttendanceStatusEnum attendanceStatusEnum = attendanceUtil
						.getStatus(trainingStartTime, trainingEndTime);
				tStudentAttendance.setStatus(attendanceStatusEnum.code);
			}
			// 備考
			tStudentAttendance.setNote(dailyAttendanceForm.getNote());
			// 更新者と更新日時
			tStudentAttendance.setLastModifiedUser(loginUserDto.getLmsUserId());
			tStudentAttendance.setLastModifiedDate(date);
			// 削除フラグ
			tStudentAttendance.setDeleteFlg(Constants.DB_FLG_FALSE);
			// 登録用Listへ追加
			tStudentAttendanceList.add(tStudentAttendance);
		}
		// 登録・更新処理
		for (TStudentAttendance tStudentAttendance : tStudentAttendanceList) {
			if (tStudentAttendance.getStudentAttendanceId() == null) {
				tStudentAttendance.setFirstCreateUser(loginUserDto.getLmsUserId());
				tStudentAttendance.setFirstCreateDate(date);
				tStudentAttendanceMapper.insert(tStudentAttendance);
			} else {
				tStudentAttendanceMapper.update(tStudentAttendance);
			}
		}
		// 完了メッセージ
		return messageUtil.getMessage(Constants.PROP_KEY_ATTENDANCE_UPDATE_NOTICE);
	}

	/**
	 * 勤怠未入力件数取得
	 * 
	 * @param lmsUserId LMSユーザーID
	 * @param deleteFlg 削除フラグ
	 * @param currentDate 現在日付
	 * @return 未入力件数
	 */
	public int getUnfilledPastCount(Integer lmsUserId, Short deleteFlg, Date currentDate) {
		// a. SimpleDateFormatクラスでフォーマットパターンを設定
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

		// 1. APIを呼び出して勤怠情報を取得
		// コースIDはログイン情報から取得
		Integer courseId = loginUserDto.getCourseId();
		List<AttendanceManagementDto> attendanceManagementDtoList = tStudentAttendanceMapper
				.getAttendanceManagement(courseId, lmsUserId, deleteFlg);

		// 取得したデータを正しく表示
		for (AttendanceManagementDto dto : attendanceManagementDtoList) {
			// 中抜け時間を設定
			if (dto.getBlankTime() != null) {
				TrainingTime blankTime = attendanceUtil.calcBlankTime(dto.getBlankTime());
				dto.setBlankTimeValue(String.valueOf(blankTime));
			}
			// 遅刻早退区分判定
			AttendanceStatusEnum statusEnum = AttendanceStatusEnum.getEnum(dto.getStatus());
			if (statusEnum != null) {
				dto.setStatusDispName(statusEnum.name);
			}
		}

		// 過去日の未入力数をカウント
		return countUnfilledPastWithFormat(attendanceManagementDtoList, currentDate, sdf);
	}

	/**
	 * SimpleDateFormatを使用した過去の未入力件数を取得
	 * 
	 * @param list 勤怠情報リスト
	 * @param today 今日の日付
	 * @param sdf 日付フォーマッター
	 * @return 過去の未入力件数
	 */
	private int countUnfilledPastWithFormat(List<AttendanceManagementDto> list, Date today, SimpleDateFormat sdf) {
		int count = 0;

		// 今日の日付を文字列に変換
		String todayStr = sdf.format(today);

		for (AttendanceManagementDto dto : list) {
			// 研修日を文字列に変換
			String trainingDateStr = sdf.format(dto.getTrainingDate());

			// 今日より過去の日付（文字列比較）
			if (trainingDateStr.compareTo(todayStr) < 0) {
				boolean startEmpty = dto.getTrainingStartTime() == null || dto.getTrainingStartTime().isEmpty();
				boolean endEmpty = dto.getTrainingEndTime() == null || dto.getTrainingEndTime().isEmpty();
				if (startEmpty || endEmpty) {
					count++;
				}
			}
		}
		return count;
	}

	/**
	 * 過去の未入力が存在するかを判定
	 *
	 * @param lmsUserId LMSユーザーID
	 * @param deleteFlg 削除フラグ
	 * @param currentDate 現在日付
	 * @return true = 未入力あり, false = 未入力なし
	 */
	public boolean hasUnfilledPastBySpecification(Integer lmsUserId, Short deleteFlg, Date currentDate) {
		int unfilledCount = getUnfilledPastCount(lmsUserId, deleteFlg, currentDate);
		// 2. 取得した未入力カウント数が0以上の場合trueを返す
		if (unfilledCount > 0) {
			return true;
		}
		// 3. それ以外はfalseを返す
		return false;
	}

	/**
	 * 勤怠フォームの入力チェック（task27）
	 * 
	 * @param attendanceForm 勤怠フォーム
	 * @return エラーメッセージ（エラーなしの場合はnull）
	 */
	public String validateAttendanceForm(AttendanceForm attendanceForm) {
		List<String> errorMessages = new ArrayList<>();
		List<String> errorFields = new ArrayList<>();

		// 勤怠リストの件数分チェックを実行
		for (int i = 0; i < attendanceForm.getAttendanceList().size(); i++) {
			DailyAttendanceForm dailyForm = attendanceForm.getAttendanceList().get(i);

			// 1. 備考の文字数チェック（100文字以内）
			if (dailyForm.getNote() != null && dailyForm.getNote().length() > 100) {
				errorMessages.add(messageUtil.getMessage("maxlength", new String[] { "備考", "100" }));
				errorFields.add("attendanceList[" + i + "].note");
			}

			// 2. 出勤時間の時・分の入力チェック
			boolean startHourEmpty = (dailyForm.getTrainingStartTimeHour() == null);
			boolean startMinuteEmpty = (dailyForm.getTrainingStartTimeMinute() == null);

			if ((startHourEmpty && !startMinuteEmpty) || (!startHourEmpty && startMinuteEmpty)) {
				errorMessages.add(messageUtil.getMessage("input.invalid", new String[] { "出勤時間" }));
				// エラーのあるフィールドのみハイライト
				if (startHourEmpty) {
					errorFields.add("attendanceList[" + i + "].trainingStartTimeHour");
				}
				if (startMinuteEmpty) {
					errorFields.add("attendanceList[" + i + "].trainingStartTimeMinute");
				}
			}

			// 3. 退勤時間の時・分の入力チェック
			boolean endHourEmpty = (dailyForm.getTrainingEndTimeHour() == null);
			boolean endMinuteEmpty = (dailyForm.getTrainingEndTimeMinute() == null);

			if ((endHourEmpty && !endMinuteEmpty) || (!endHourEmpty && endMinuteEmpty)) {
				errorMessages.add(messageUtil.getMessage("input.invalid", new String[] { "退勤時間" }));
				// エラーのあるフィールドのみハイライト
				if (endHourEmpty) {
					errorFields.add("attendanceList[" + i + "].trainingEndTimeHour");
				}
				if (endMinuteEmpty) {
					errorFields.add("attendanceList[" + i + "].trainingEndTimeMinute");
				}
			}

			// 4. 出勤情報がない場合の退勤情報入力チェック
			boolean startTimeEmpty = startHourEmpty && startMinuteEmpty;
			boolean endTimeExists = !endHourEmpty && !endMinuteEmpty;

			if (startTimeEmpty && endTimeExists) {
				errorMessages.add(messageUtil.getMessage("attendance.punchInEmpty"));
				errorFields.add("attendanceList[" + i + "].trainingStartTimeHour");
				errorFields.add("attendanceList[" + i + "].trainingStartTimeMinute");
			}

			// 5. 時刻形式と時刻範囲のチェック
			combineTrainingTime(dailyForm);

			// 出勤時間の時刻形式チェック
			if (!isEmptyString(dailyForm.getTrainingStartTime())
					&& !isValidTimeFormat(dailyForm.getTrainingStartTime())) {
				errorMessages.add(messageUtil.getMessage("trainingTime", new String[] { "出勤時間" }));
				errorFields.add("attendanceList[" + i + "].trainingStartTimeHour");
				errorFields.add("attendanceList[" + i + "].trainingStartTimeMinute");
			}

			// 退勤時間の時刻形式チェック
			if (!isEmptyString(dailyForm.getTrainingEndTime()) && !isValidTimeFormat(dailyForm.getTrainingEndTime())) {
				errorMessages.add(messageUtil.getMessage("trainingTime", new String[] { "退勤時間" }));
				errorFields.add("attendanceList[" + i + "].trainingEndTimeHour");
				errorFields.add("attendanceList[" + i + "].trainingEndTimeMinute");
			}

			// 時刻範囲チェック（出勤時間 > 退勤時間）
			if (!isEmptyString(dailyForm.getTrainingStartTime()) && !isEmptyString(dailyForm.getTrainingEndTime())) {
				try {
					TrainingTime startTime = new TrainingTime(dailyForm.getTrainingStartTime());
					TrainingTime endTime = new TrainingTime(dailyForm.getTrainingEndTime());

					if (startTime.compareTo(endTime) >= 0) {
						errorMessages.add(messageUtil.getMessage("attendance.trainingTimeRange",
								new String[] { dailyForm.getTrainingStartTime(), dailyForm.getTrainingEndTime() }));
						errorFields.add("attendanceList[" + i + "].trainingStartTimeHour");
						errorFields.add("attendanceList[" + i + "].trainingStartTimeMinute");
						errorFields.add("attendanceList[" + i + "].trainingEndTimeHour");
						errorFields.add("attendanceList[" + i + "].trainingEndTimeMinute");
					}

					// 6. 中抜け時間が勤務時間を超えるチェック
					if (dailyForm.getBlankTime() != null && dailyForm.getBlankTime() > 0) {
						int workMinutes = (endTime.getHour() * 60 + endTime.getMinute()) -
								(startTime.getHour() * 60 + startTime.getMinute());

						if (workMinutes > 0 && dailyForm.getBlankTime() > workMinutes) {
							errorMessages.add(messageUtil.getMessage("attendance.blankTimeError"));
							errorFields.add("attendanceList[" + i + "].blankTime");
						}
					}
				} catch (Exception e) {
					// TrainingTimeの生成エラーの場合は時刻形式エラーとして扱う
					if (!isEmptyString(dailyForm.getTrainingStartTime())) {
						errorMessages.add(messageUtil.getMessage("trainingTime", new String[] { "出勤時間" }));
						errorFields.add("attendanceList[" + i + "].trainingStartTimeHour");
						errorFields.add("attendanceList[" + i + "].trainingStartTimeMinute");
					}
					if (!isEmptyString(dailyForm.getTrainingEndTime())) {
						errorMessages.add(messageUtil.getMessage("trainingTime", new String[] { "退勤時間" }));
						errorFields.add("attendanceList[" + i + "].trainingEndTimeHour");
						errorFields.add("attendanceList[" + i + "].trainingEndTimeMinute");
					}
				}
			}
		}

		// エラーがある場合、エラー情報をAttendanceFormに設定
		if (!errorMessages.isEmpty()) {
			attendanceForm.setErrorFields(errorFields);
			return String.join("\n", errorMessages);
		}

		return null; // エラーなし
	}

	/**
	 * 文字列が空かどうかをチェック
	 * 
	 * @param str チェック対象文字列
	 * @return true: 空、false: 空でない
	 */
	private boolean isEmptyString(String str) {
		return str == null || str.trim().isEmpty();
	}

	/**
	 * 時刻形式（HH:mm）かどうかをチェック
	 * 
	 * @param timeStr 時刻文字列
	 * @return true: 正しい形式、false: 不正な形式
	 */
	private boolean isValidTimeFormat(String timeStr) {
		if (timeStr == null || timeStr.trim().isEmpty()) {
			return false;
		}

		String[] parts = timeStr.split(":");
		if (parts.length != 2) {
			return false;
		}

		try {
			int hour = Integer.parseInt(parts[0]);
			int minute = Integer.parseInt(parts[1]);

			return (hour >= 0 && hour <= 23) && (minute >= 0 && minute <= 59);
		} catch (NumberFormatException e) {
			return false;
		}
	}

	/**
	 * 時分を結合してhh:mm形式の文字列を作成する
	 * HTML画面との連携で必要な処理
	 * 
	 * @param dailyForm 日次勤怠フォーム
	 */
	private void combineTrainingTime(DailyAttendanceForm dailyForm) {
		// 出勤時間を結合
		if (dailyForm.getTrainingStartTimeHour() != null && dailyForm.getTrainingStartTimeMinute() != null) {
			dailyForm.setTrainingStartTime(String.format("%02d:%02d",
					dailyForm.getTrainingStartTimeHour(), dailyForm.getTrainingStartTimeMinute()));
		} else {
			dailyForm.setTrainingStartTime("");
		}

		// 退勤時間を結合
		if (dailyForm.getTrainingEndTimeHour() != null && dailyForm.getTrainingEndTimeMinute() != null) {
			dailyForm.setTrainingEndTime(String.format("%02d:%02d",
					dailyForm.getTrainingEndTimeHour(), dailyForm.getTrainingEndTimeMinute()));
		} else {
			dailyForm.setTrainingEndTime("");
		}
	}
}