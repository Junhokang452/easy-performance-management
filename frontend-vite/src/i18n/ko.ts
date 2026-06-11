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
  nav: {
    hr: {
      cycles: string;
    };
  };
  cycles: {
    title: string;
    create: string;
    empty: string;
    policy: {
      notSet: string;
    };
    action: {
      edit: string;
      transition: string;
      policy: string;
      delete: string;
    };
    field: {
      name: string;
      periodStart: string;
      periodEnd: string;
      cycleType: string;
      status: string;
    };
    type: {
      HALF_ANNUAL: string;
      ANNUAL: string;
      QUARTERLY: string;
      MONTHLY: string;
      CUSTOM: string;
    };
    status: {
      PLANNED: string;
      ACTIVE: string;
      GOAL_SETTING: string;
      MID_REVIEW: string;
      SELF_REVIEW: string;
      MANAGER_REVIEW: string;
      CALIBRATION: string;
      FINALIZED: string;
      CANCELLED: string;
    };
  };
  policy: {
    title: string;
    field: {
      distributionMode: string;
      ratingScale: string;
      appealEnabled: string;
      bscEnabled: string;
      achievementLogCutoffDays: string;
      forcedDistribution: string;
    };
    distributionMode: {
      HYBRID: string;
      FORCED: string;
      ABSOLUTE: string;
    };
    ratingScale: {
      S_A_B_C_D: string;
      ONE_TO_FIVE: string;
      ONE_TO_HUNDRED: string;
    };
    distributionSum: string;
    distributionSumMustBeOne: string;
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
    E9804441: string;
    E9804231: string;
    E9804921: string;
    E9804232: string;
    E9804922: string;
    E9804442: string;
    E9804233: string;
    E9804234: string;
    E9804235: string;
    E9804923: string;
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
  nav: {
    hr: {
      cycles: 'HR > 사이클 관리',
    },
  },
  cycles: {
    title: '평가 사이클',
    create: '사이클 생성',
    empty: '사이클이 없습니다',
    policy: {
      notSet: '정책 미설정',
    },
    action: {
      edit: '편집',
      transition: '상태 전이',
      policy: '정책 편집',
      delete: '삭제',
    },
    field: {
      name: '이름',
      periodStart: '시작일',
      periodEnd: '종료일',
      cycleType: '사이클 유형',
      status: '상태',
    },
    type: {
      HALF_ANNUAL: '반기',
      ANNUAL: '연간',
      QUARTERLY: '분기',
      MONTHLY: '월간',
      CUSTOM: '사용자 정의',
    },
    status: {
      PLANNED: '계획',
      ACTIVE: '활성',
      GOAL_SETTING: '목표 설정',
      MID_REVIEW: '중간 평가',
      SELF_REVIEW: '자기평가',
      MANAGER_REVIEW: '매니저 평가',
      CALIBRATION: 'Calibration',
      FINALIZED: '확정',
      CANCELLED: '취소',
    },
  },
  policy: {
    title: '평가 정책',
    field: {
      distributionMode: '분포 정책',
      ratingScale: '평가 척도',
      appealEnabled: '이의신청 활성',
      bscEnabled: 'BSC 4 관점 활성',
      achievementLogCutoffDays: '사후 신고 cutoff (일)',
      forcedDistribution: '강제 분포',
    },
    distributionMode: {
      HYBRID: '혼합',
      FORCED: '강제',
      ABSOLUTE: '절대',
    },
    ratingScale: {
      S_A_B_C_D: 'S/A/B/C/D',
      ONE_TO_FIVE: '1~5점',
      ONE_TO_HUNDRED: '1~100점',
    },
    distributionSum: '합계',
    distributionSumMustBeOne: '분포의 합은 1.0 이어야 합니다 (현재: {sum})',
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
    E9804441: '사이클을 찾을 수 없습니다',
    E9804231: '허용되지 않은 상태 전이입니다',
    E9804921: '동일한 이름의 사이클이 이미 존재합니다',
    E9804232: '기간이 올바르지 않습니다 (종료일이 시작일 이후여야 함)',
    E9804922: '활성 이상 상태의 사이클은 삭제할 수 없습니다',
    E9804442: '정책을 찾을 수 없습니다',
    E9804233: '분포 정책의 합계가 1.0 이 아닙니다',
    E9804234: '평가 척도와 일치하지 않는 분포 키입니다',
    E9804235: 'FORCED 모드는 분포 설정이 필수입니다',
    E9804923: '사이클이 활성화된 후에는 정책의 분포 모드/척도를 변경할 수 없습니다',
  },
};

export type I18nDict = I18nShape;
