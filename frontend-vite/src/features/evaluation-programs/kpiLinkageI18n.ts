export const kpiLinkageKo = {
  title: 'KPI 근거 연결', description: '합의된 목표 하나에 KPI 배정 하나를 명시적으로 연결합니다. 목표값과 가중치는 근거 저장 시점의 현재 정의를 사용합니다.',
  ready: '연결 가능', sourceMissing: '실적 없음', blocked: '연결 불가',
  programAsOfDate: '조직 기준일', cutoffDate: '실적 마감 기준일', goal: '평가 목표', node: 'KPI 항목', targetCaptured: '저장 시점 목표값', actual: '실적',
  achievementRate: '달성 비율', autoScore: '참고 자동점수', formula: '산식', reasonCode: '확인 사항',
  evidenceOnly: '참고 근거만 저장합니다. 목표·가중치·평가 제출·최종 점수는 변경하지 않습니다.',
  evidenceHistory: 'KPI 근거 이력', revision: '버전', active: '현재 근거', superseded: '이전 근거', capturedAt: '저장 일시', reason: '연결·갱신 사유',
  cycle: 'KPI 주기', kpiAssignment: 'KPI 배정', selectionSummary: '연결 대상', preview: '근거 미리보기', apply: '근거 저장', applySuccess: 'KPI 근거를 저장했습니다.',
  stale: '원본 또는 현재 근거가 변경되었습니다. 다시 미리보기한 뒤 저장하세요.', confirmTitle: 'KPI 근거 저장 확인', confirmBody: '선택한 목표의 근거를 새 버전으로 저장합니다. 이전 근거는 보존됩니다.',
  previousPage: '이전 페이지', nextPage: '다음 페이지', emptyHistory: '저장된 KPI 근거가 없습니다.',
  goalNotFrozen: '합의되었거나 실적이 보고된 목표만 연결할 수 있습니다.', targetInvalid: '목표값은 0보다 커야 합니다.', weightInvalid: 'KPI 가중치는 0보다 커야 합니다.', actualMissing: '기준일까지 유효한 실적이 없습니다.',
} as const;
type KpiLinkageLabels = { [K in keyof typeof kpiLinkageKo]: string };

export const kpiLinkageEn: KpiLinkageLabels = {
  title: 'KPI evidence linkage', description: 'Explicitly link one agreed goal to one KPI assignment. Target and weight use the current definition at capture time.',
  ready: 'Ready', sourceMissing: 'No actual', blocked: 'Blocked',
  programAsOfDate: 'Organization reference date', cutoffDate: 'Actual cutoff date', goal: 'Evaluation goal', node: 'KPI item', targetCaptured: 'Target at capture', actual: 'Actual',
  achievementRate: 'Achievement ratio', autoScore: 'Reference auto-score', formula: 'Formula', reasonCode: 'Check required',
  evidenceOnly: 'Saves reference evidence only. Goals, weights, review submissions and final scores remain unchanged.',
  evidenceHistory: 'KPI evidence history', revision: 'Revision', active: 'Current evidence', superseded: 'Previous evidence', capturedAt: 'Captured at', reason: 'Link or refresh reason',
  cycle: 'KPI cycle', kpiAssignment: 'KPI assignment', selectionSummary: 'Selected linkage', preview: 'Preview evidence', apply: 'Save evidence', applySuccess: 'KPI evidence saved.',
  stale: 'The source or current evidence changed. Preview again before saving.', confirmTitle: 'Confirm KPI evidence', confirmBody: 'Save a new evidence revision for the selected goal. Previous evidence is retained.',
  previousPage: 'Previous page', nextPage: 'Next page', emptyHistory: 'No saved KPI evidence.',
  goalNotFrozen: 'Only agreed or self-reported goals can be linked.', targetInvalid: 'Target must be greater than zero.', weightInvalid: 'KPI weight must be greater than zero.', actualMissing: 'No valid actual exists on or before the cutoff date.',
};

export const kpiLinkageJa: KpiLinkageLabels = {
  title: 'KPI根拠の連携', description: '合意済みの目標一つにKPI割当一つを明示的に連携します。目標値とウェイトは保存時点の定義を使用します。',
  ready: '連携可能', sourceMissing: '実績なし', blocked: '連携不可',
  programAsOfDate: '組織基準日', cutoffDate: '実績締切基準日', goal: '評価目標', node: 'KPI項目', targetCaptured: '保存時点の目標値', actual: '実績',
  achievementRate: '達成比率', autoScore: '参考自動スコア', formula: '計算式', reasonCode: '確認事項',
  evidenceOnly: '参考根拠のみ保存します。目標・ウェイト・評価提出・最終スコアは変更しません。',
  evidenceHistory: 'KPI根拠履歴', revision: 'バージョン', active: '現在の根拠', superseded: '以前の根拠', capturedAt: '保存日時', reason: '連携・更新理由',
  cycle: 'KPI期間', kpiAssignment: 'KPI割当', selectionSummary: '連携対象', preview: '根拠プレビュー', apply: '根拠を保存', applySuccess: 'KPI根拠を保存しました。',
  stale: '元データまたは現在の根拠が変更されました。再度プレビューして保存してください。', confirmTitle: 'KPI根拠の保存確認', confirmBody: '選択した目標の根拠を新しいバージョンで保存します。以前の根拠は保持されます。',
  previousPage: '前のページ', nextPage: '次のページ', emptyHistory: '保存されたKPI根拠はありません。',
  goalNotFrozen: '合意済みまたは実績報告済みの目標のみ連携できます。', targetInvalid: '目標値は0より大きい必要があります。', weightInvalid: 'KPIウェイトは0より大きい必要があります。', actualMissing: '基準日以前の有効な実績がありません。',
};

export const kpiLinkageZhCN: KpiLinkageLabels = {
  title: '关联KPI依据', description: '将一个已确认的目标明确关联至一个KPI分配。目标值和权重采用保存时的当前定义。',
  ready: '可关联', sourceMissing: '无实际值', blocked: '不可关联',
  programAsOfDate: '组织基准日', cutoffDate: '实际值截止日', goal: '评价目标', node: 'KPI项目', targetCaptured: '保存时目标值', actual: '实际值',
  achievementRate: '达成比率', autoScore: '参考自动评分', formula: '公式', reasonCode: '待确认事项',
  evidenceOnly: '仅保存参考依据，不修改目标、权重、评价提交或最终评分。',
  evidenceHistory: 'KPI依据历史', revision: '版本', active: '当前依据', superseded: '历史依据', capturedAt: '保存时间', reason: '关联或更新原因',
  cycle: 'KPI周期', kpiAssignment: 'KPI分配', selectionSummary: '关联对象', preview: '预览依据', apply: '保存依据', applySuccess: '已保存KPI依据。',
  stale: '源数据或当前依据已变更，请重新预览后保存。', confirmTitle: '确认保存KPI依据', confirmBody: '将为所选目标保存新版本依据，保留历史依据。',
  previousPage: '上一页', nextPage: '下一页', emptyHistory: '暂无已保存的KPI依据。',
  goalNotFrozen: '仅可关联已确认或已报告实际值的目标。', targetInvalid: '目标值必须大于零。', weightInvalid: 'KPI权重必须大于零。', actualMissing: '截止日及之前无有效实际值。',
};

export const kpiLinkageVi: KpiLinkageLabels = {
  title: 'Liên kết minh chứng KPI', description: 'Liên kết rõ ràng một mục tiêu đã thống nhất với một phân công KPI. Mục tiêu và trọng số dùng định nghĩa hiện tại khi lưu.',
  ready: 'Có thể liên kết', sourceMissing: 'Chưa có thực tế', blocked: 'Không thể liên kết',
  programAsOfDate: 'Ngày tham chiếu tổ chức', cutoffDate: 'Ngày chốt số thực tế', goal: 'Mục tiêu đánh giá', node: 'Hạng mục KPI', targetCaptured: 'Mục tiêu tại thời điểm lưu', actual: 'Thực tế',
  achievementRate: 'Tỷ lệ hoàn thành', autoScore: 'Điểm tự động tham khảo', formula: 'Công thức', reasonCode: 'Cần kiểm tra',
  evidenceOnly: 'Chỉ lưu minh chứng tham khảo. Không thay đổi mục tiêu, trọng số, bài đánh giá đã nộp hay điểm cuối cùng.',
  evidenceHistory: 'Lịch sử minh chứng KPI', revision: 'Phiên bản', active: 'Minh chứng hiện tại', superseded: 'Minh chứng trước', capturedAt: 'Thời điểm lưu', reason: 'Lý do liên kết hoặc cập nhật',
  cycle: 'Chu kỳ KPI', kpiAssignment: 'Phân công KPI', selectionSummary: 'Liên kết đã chọn', preview: 'Xem trước minh chứng', apply: 'Lưu minh chứng', applySuccess: 'Đã lưu minh chứng KPI.',
  stale: 'Nguồn hoặc minh chứng hiện tại đã thay đổi. Hãy xem trước lại rồi lưu.', confirmTitle: 'Xác nhận minh chứng KPI', confirmBody: 'Lưu phiên bản minh chứng mới cho mục tiêu đã chọn. Minh chứng trước được giữ lại.',
  previousPage: 'Trang trước', nextPage: 'Trang sau', emptyHistory: 'Chưa có minh chứng KPI được lưu.',
  goalNotFrozen: 'Chỉ liên kết mục tiêu đã thống nhất hoặc đã tự báo cáo.', targetInvalid: 'Giá trị mục tiêu phải lớn hơn 0.', weightInvalid: 'Trọng số KPI phải lớn hơn 0.', actualMissing: 'Không có số thực tế hợp lệ tính đến ngày chốt.',
};
