/**
 * 한국어 i18n bundle — performance FE 단계 4 진입 기본.
 *
 * Namespace 5 계층 (ADR-027 정합):
 * - common.action / status / message / label
 * - domain.app / nav / selfEvaluation / personalOkr / reflectionJournal / mentorFeedback
 * - error.E97* (performance 영역 prefix 가정)
 *
 * 5 locale (ko + en + ja + zh-CN + zh-TW) 확장은 lib FE 13 i18n-common 진입 후.
 */
interface I18nShape {
  common: {
    action: {
      create: string;
      save: string;
      cancel: string;
      edit: string;
      delete: string;
      submit: string;
      close: string;
      retry: string;
    };
    status: {
      active: string;
      inactive: string;
      loading: string;
      empty: string;
    };
    message: {
      created: string;
      updated: string;
      deleted: string;
      loadError: string;
    };
    label: {
      darkMode: string;
      language: string;
      logout: string;
    };
  };
  domain: {
    app: {
      title: string;
      subtitle: string;
    };
    nav: {
      section: string;
      selfEvaluation: string;
      personalOkr: string;
      reflectionJournal: string;
      mentorFeedback: string;
    };
    selfEvaluation: {
      title: string;
      description: string;
      empty: string;
      emptyDescription: string;
      period: string;
      content: string;
      score: string;
      status: string;
      statusDraft: string;
      statusSubmitted: string;
      statusReviewed: string;
      statusFinalized: string;
    };
    personalOkr: {
      title: string;
      description: string;
      empty: string;
      emptyDescription: string;
      objective: string;
      progress: string;
      period: string;
      status: string;
      statusActive: string;
      statusAtRisk: string;
      statusCompleted: string;
      statusArchived: string;
    };
    reflectionJournal: {
      title: string;
      description: string;
      empty: string;
      emptyDescription: string;
      reflectionDate: string;
      method: string;
      content: string;
      isPrivate: string;
      methodKpt: string;
      methodFourLs: string;
      methodSsc: string;
    };
    mentorFeedback: {
      title: string;
      description: string;
      empty: string;
      emptyDescription: string;
      feedbackDate: string;
      mentor: string;
      mentee: string;
      category: string;
      content: string;
      acknowledged: string;
      categoryGrowth: string;
      categoryRecognition: string;
      categoryCoaching: string;
      categoryConversation: string;
    };
  };
  error: {
    boundary: string;
    unknown: string;
    network: string;
    unauthorized: string;
    forbidden: string;
  };
}

export const ko: I18nShape = {
  common: {
    action: {
      create: '추가',
      save: '저장',
      cancel: '취소',
      edit: '수정',
      delete: '삭제',
      submit: '제출',
      close: '닫기',
      retry: '다시 시도',
    },
    status: {
      active: '활성',
      inactive: '비활성',
      loading: '불러오는 중...',
      empty: '데이터가 없습니다',
    },
    message: {
      created: '추가되었습니다.',
      updated: '수정되었습니다.',
      deleted: '삭제되었습니다.',
      loadError: '불러오기 실패',
    },
    label: {
      darkMode: '다크 모드',
      language: '언어',
      logout: '로그아웃',
    },
  },
  domain: {
    app: {
      title: 'easy-performance-management',
      subtitle: '성과 관리 (Performance Management)',
    },
    nav: {
      section: '성과 관리',
      selfEvaluation: '자기평가',
      personalOkr: '개인 OKR',
      reflectionJournal: '회고 저널',
      mentorFeedback: '멘토 피드백',
    },
    selfEvaluation: {
      title: '자기평가',
      description: '분기/연간 자기평가 작성 및 제출',
      empty: '등록된 자기평가가 없습니다',
      emptyDescription: '새 자기평가를 시작하여 분기 평가를 진행하세요.',
      period: '평가 기간',
      content: '내용',
      score: '자기 점수',
      status: '상태',
      statusDraft: '작성 중',
      statusSubmitted: '제출됨',
      statusReviewed: '리뷰 완료',
      statusFinalized: '확정',
    },
    personalOkr: {
      title: '개인 OKR',
      description: '분기별 개인 목표 및 핵심 결과 (Objective + Key Results)',
      empty: '등록된 OKR이 없습니다',
      emptyDescription: '분기 시작 시 OKR을 등록하여 개인 목표를 추적하세요.',
      objective: '목표 (Objective)',
      progress: '진행률',
      period: '기간',
      status: '상태',
      statusActive: '진행 중',
      statusAtRisk: '위험',
      statusCompleted: '완료',
      statusArchived: '보관됨',
    },
    reflectionJournal: {
      title: '회고 저널',
      description: 'KPT / 4Ls / SSC 회고 방법론 기반 저널',
      empty: '등록된 회고가 없습니다',
      emptyDescription: '회고 방법론을 선택하여 첫 회고를 작성하세요.',
      reflectionDate: '회고일',
      method: '방법론',
      content: '내용',
      isPrivate: '비공개',
      methodKpt: 'KPT (Keep/Problem/Try)',
      methodFourLs: '4Ls (Liked/Learned/Lacked/Longed-for)',
      methodSsc: 'SSC (Stop/Start/Continue)',
    },
    mentorFeedback: {
      title: '멘토 피드백',
      description: '매니저-팀원 1:1 피드백 (Growth / Recognition / Coaching / Conversation)',
      empty: '등록된 피드백이 없습니다',
      emptyDescription: '첫 1:1 피드백을 기록하여 팀원의 성장을 지원하세요.',
      feedbackDate: '피드백 일자',
      mentor: '멘토',
      mentee: '멘티',
      category: '카테고리',
      content: '내용',
      acknowledged: '확인됨',
      categoryGrowth: '성장 / 학습',
      categoryRecognition: '인정 / 강점',
      categoryCoaching: '코칭 / 개선',
      categoryConversation: '1:1 대화',
    },
  },
  error: {
    boundary: '페이지 렌더링 오류',
    unknown: '알 수 없는 오류가 발생했습니다.',
    network: '네트워크 오류가 발생했습니다.',
    unauthorized: '로그인이 필요합니다.',
    forbidden: '권한이 없습니다.',
  },
};

export type I18nDict = I18nShape;
