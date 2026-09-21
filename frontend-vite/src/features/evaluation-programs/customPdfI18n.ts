// Glossary: lib/easy-platform/easy-standards/glossary/i18n-terms.md §2.
const ko = {
  action: '맞춤 PDF', title: '보고서 제목', language: '출력 언어', korean: '한국어', english: '영어',
  orientation: '용지 방향', portrait: '세로', landscape: '가로', summary: '등급 요약', participants: '사원별 결과',
  generate: '다운로드', cancel: '취소', columns: '출력 열', sections: '출력 섹션',
  hint: '확정·공개된 결과만 출력합니다. 최대 200명, 한국어·영어 출력 지원. 긴 셀은 ...로 생략됩니다.',
  invalidOptions: '한 개 이상의 섹션이 필요합니다. 표에는 이름과 점수 또는 등급을 포함해 주세요. 세로는 최대 5열, 가로는 최대 8열입니다.',
  error: 'PDF를 생성하지 못했습니다. 옵션과 지원 문자를 확인한 후 다시 시도해 주세요.',
  columnLabels: { EMPLOYEE_NO: '사번', EMPLOYEE_NAME: '사원', DEPARTMENT: '부서', POSITION: '직책', JOB: '직무', SCORE: '점수', GRADE: '평가등급', FEEDBACK_STATUS: '피드백 상태' },
};
type Labels = { [K in keyof typeof ko]: typeof ko[K] extends string ? string : { [C in keyof typeof ko[K]]: string } };
const en: Labels = {
  action: 'Custom PDF', title: 'Report title', language: 'Output language', korean: 'Korean', english: 'English',
  orientation: 'Orientation', portrait: 'Portrait', landscape: 'Landscape', summary: 'Grade summary', participants: 'Employee results',
  generate: 'Download', cancel: 'Cancel', columns: 'Output columns', sections: 'Output sections',
  hint: 'Only finalized, published results are included. Up to 200 participants; Korean or English output. Long cells are shortened with ... .',
  invalidOptions: 'Select at least one section. Tables require a name and a score or grade. Maximum 5 columns in portrait or 8 in landscape.',
  error: 'PDF generation failed. Check the options and supported characters, then try again.',
  columnLabels: { EMPLOYEE_NO: 'Employee no.', EMPLOYEE_NAME: 'Employee', DEPARTMENT: 'Department', POSITION: 'Position', JOB: 'Job', SCORE: 'Score', GRADE: 'Result grade', FEEDBACK_STATUS: 'Feedback status' },
};
const ja: Labels = {
  action: 'カスタムPDF', title: 'レポートのタイトル', language: '出力言語', korean: '韓国語', english: '英語',
  orientation: '用紙の向き', portrait: '縦', landscape: '横', summary: '評価等級の概要', participants: '社員別の結果',
  generate: 'ダウンロード', cancel: 'キャンセル', columns: '出力列', sections: '出力セクション',
  hint: '確定・公開済みの結果のみ出力します。最大200名、韓国語・英語に対応。長いセルは ... で省略されます。',
  invalidOptions: 'セクションを1つ以上選択してください。表には氏名と点数または等級が必要です。縦は最大5列、横は最大8列です。',
  error: 'PDFを生成できませんでした。オプションと対応文字を確認して、再度お試しください。',
  columnLabels: { EMPLOYEE_NO: '社員番号', EMPLOYEE_NAME: '社員', DEPARTMENT: '部署', POSITION: '役職', JOB: '職務', SCORE: '点数', GRADE: '評価等級', FEEDBACK_STATUS: 'フィードバック状況' },
};
const zhCN: Labels = {
  action: '自定义PDF', title: '报告标题', language: '输出语言', korean: '韩语', english: '英语',
  orientation: '纸张方向', portrait: '纵向', landscape: '横向', summary: '评级摘要', participants: '员工结果',
  generate: '下载', cancel: '取消', columns: '输出列', sections: '输出部分',
  hint: '仅输出已确定并公开的结果。最多200人，支持韩语和英语。过长的单元格将以 ... 省略。',
  invalidOptions: '请至少选择一个部分。表格必须包含姓名以及分数或评级。纵向最多5列，横向最多8列。',
  error: '无法生成PDF。请检查选项和支持的字符后重试。',
  columnLabels: { EMPLOYEE_NO: '工号', EMPLOYEE_NAME: '员工', DEPARTMENT: '部门', POSITION: '职位', JOB: '职务', SCORE: '分数', GRADE: '评估等级', FEEDBACK_STATUS: '反馈状态' },
};
const vi: Labels = {
  action: 'PDF tùy chỉnh', title: 'Tiêu đề báo cáo', language: 'Ngôn ngữ xuất', korean: 'Tiếng Hàn', english: 'Tiếng Anh',
  orientation: 'Hướng trang', portrait: 'Dọc', landscape: 'Ngang', summary: 'Tóm tắt xếp loại', participants: 'Kết quả nhân viên',
  generate: 'Tải xuống', cancel: 'Hủy', columns: 'Cột xuất', sections: 'Phần xuất',
  hint: 'Chỉ xuất kết quả đã chốt và công bố. Tối đa 200 người, hỗ trợ tiếng Hàn và tiếng Anh. Ô dài được rút gọn bằng ... .',
  invalidOptions: 'Chọn ít nhất một phần. Bảng phải có tên và điểm hoặc xếp loại. Tối đa 5 cột cho trang dọc hoặc 8 cột cho trang ngang.',
  error: 'Không thể tạo PDF. Hãy kiểm tra tùy chọn và ký tự được hỗ trợ rồi thử lại.',
  columnLabels: { EMPLOYEE_NO: 'Mã nhân viên', EMPLOYEE_NAME: 'Nhân viên', DEPARTMENT: 'Phòng ban', POSITION: 'Chức vụ', JOB: 'Công việc', SCORE: 'Điểm', GRADE: 'Xếp loại đánh giá', FEEDBACK_STATUS: 'Trạng thái phản hồi' },
};
export const customPdfI18n = { ko, en, ja, 'zh-CN': zhCN, vi };
