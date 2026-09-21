export const auditKo = {
  title: '운영 감사 이력', description: '기록된 변경의 시각·행위자·사유를 확인합니다. 이전 기록은 수정할 수 없습니다.',
  event: '변경 유형', actor: '행위자', reason: '사유', previous: '이전', next: '다음', refresh: '새로고침',
  unknownActor: '직원 연결 정보 없음', programScope: '평가 전체',
  events: ['평가 생성', '기본 정보 변경', '평가 기준 개정', '평가 개시', '대상자 생성', '대상자 변경', '평가자 변경', '단계 변경', '목표 변경', '평가 제출', '결과 계산', '결과 조정', '피드백 변경', '피드백 무효화', '평가 마감', '마감 취소', '결과 공개', '직원 화면 미리보기'],
};
export type AuditLabels = { [K in keyof typeof auditKo]: typeof auditKo[K] extends string[] ? string[] : string };
export const auditEn: AuditLabels = {
  title: 'Operation audit history', description: 'Review recorded changes, actors and reasons. Historical records cannot be edited.',
  event: 'Event', actor: 'Actor', reason: 'Reason', previous: 'Previous', next: 'Next', refresh: 'Refresh', unknownActor: 'No employee binding', programScope: 'Whole evaluation',
  events: ['Evaluation created', 'Basic information changed', 'Definition revised', 'Evaluation opened', 'Participant created', 'Participant changed', 'Reviewers changed', 'Stage changed', 'Goal changed', 'Review submitted', 'Results calculated', 'Results adjusted', 'Feedback changed', 'Feedback invalidated', 'Evaluation finalized', 'Finalization cancelled', 'Results published', 'Employee screen previewed'],
};
export const auditJa: AuditLabels = {
  title: '運用監査履歴', description: '変更日時・実行者・理由を確認します。過去の記録は編集できません。',
  event: '変更種別', actor: '実行者', reason: '理由', previous: '前へ', next: '次へ', refresh: '更新', unknownActor: '従業員の紐付けなし', programScope: '評価全体',
  events: ['評価作成', '基本情報変更', '評価基準改訂', '評価開始', '対象者作成', '対象者変更', '評価者変更', '段階変更', '目標変更', '評価提出', '結果計算', '結果調整', 'フィードバック変更', 'フィードバック無効化', '評価締切', '締切取消', '結果公開', '従業員画面プレビュー'],
};
export const auditZhCN: AuditLabels = {
  title: '操作审计记录', description: '查看变更时间、操作人和原因。历史记录不可修改。',
  event: '变更类型', actor: '操作人', reason: '原因', previous: '上一页', next: '下一页', refresh: '刷新', unknownActor: '未关联员工', programScope: '整个评价',
  events: ['创建评价', '修改基本信息', '修订评价标准', '启动评价', '创建被评人', '修改被评人', '修改评价人', '变更阶段', '修改目标', '提交评价', '计算结果', '调整结果', '修改反馈', '反馈失效', '评价结案', '取消结案', '发布结果', '预览员工页面'],
};
export const auditVi: AuditLabels = {
  title: 'Nhật ký kiểm toán vận hành', description: 'Xem thời gian, người thực hiện và lý do thay đổi. Không thể sửa bản ghi cũ.',
  event: 'Loại thay đổi', actor: 'Người thực hiện', reason: 'Lý do', previous: 'Trước', next: 'Sau', refresh: 'Làm mới', unknownActor: 'Chưa liên kết nhân viên', programScope: 'Toàn bộ kỳ đánh giá',
  events: ['Tạo kỳ đánh giá', 'Đổi thông tin cơ bản', 'Sửa tiêu chí đánh giá', 'Mở kỳ đánh giá', 'Tạo người được đánh giá', 'Đổi người được đánh giá', 'Đổi người đánh giá', 'Đổi giai đoạn', 'Đổi mục tiêu', 'Nộp đánh giá', 'Tính kết quả', 'Điều chỉnh kết quả', 'Đổi phản hồi', 'Vô hiệu phản hồi', 'Chốt kỳ đánh giá', 'Hủy chốt', 'Công bố kết quả', 'Xem trước màn hình nhân viên'],
};
