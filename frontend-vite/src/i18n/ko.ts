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
    kpi: {
      my: string;
      managerTree: string;
      directorTree: string;
    };
    review: {
      self: string;
      manager: string;
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
  kpi: {
    selectCycle: string;
    noCycle: string;
    cyclePlaceholder: string;
    level: {
      CORPORATE: string;
      DIVISION: string;
      TEAM: string;
      INDIVIDUAL: string;
    };
    bscPerspective: {
      FINANCIAL: string;
      CUSTOMER: string;
      INTERNAL_PROCESS: string;
      LEARNING_GROWTH: string;
      UNASSIGNED: string;
    };
    source: {
      MANUAL: string;
      HCM: string;
      EXTERNAL: string;
    };
    weightBadge: {
      complete: string;
      incomplete: string;
      exceeded: string;
    };
    my: {
      title: string;
      description: string;
      employeeId: string;
      employeeIdPlaceholder: string;
      load: string;
      empty: string;
      emptyHint: string;
      needInput: string;
      col: {
        node: string;
        tree: string;
        weight: string;
        target: string;
        latestActual: string;
        achievementRate: string;
      };
      reportActual: string;
      actualHistory: string;
    };
    manager: {
      title: string;
      description: string;
      treeList: string;
      createTree: string;
      empty: string;
      emptyTree: string;
      addRootNode: string;
      addChild: string;
      editNode: string;
      deleteNode: string;
      manageAssignments: string;
      editTree: string;
      deleteTree: string;
      childWeightSum: string;
    };
    director: {
      title: string;
      description: string;
      bscToggle: string;
      readonly: string;
      empty: string;
    };
    tree: {
      name: string;
      level: string;
      ownerOrgUnitId: string;
      bscEnabled: string;
      create: string;
    };
    node: {
      label: string;
      weight: string;
      target: string;
      unit: string;
      bscPerspective: string;
      source: string;
      cascadeFromId: string;
      parent: string;
      parentRoot: string;
      assignments: string;
      assignmentCount: string;
      create: string;
      weightHint: string;
    };
    assignment: {
      title: string;
      employeeId: string;
      weight: string;
      weightOverrideHint: string;
      targetOverride: string;
      add: string;
      empty: string;
      effectiveWeight: string;
      effectiveTarget: string;
    };
    actual: {
      title: string;
      asOfDate: string;
      actualValue: string;
      evidenceUrl: string;
      comment: string;
      report: string;
      history: string;
      empty: string;
      superseded: string;
      latest: string;
      correct: string;
      correctTitle: string;
      reportedAt: string;
    };
    confirmDeleteNode: string;
    confirmDeleteTree: string;
    confirmDeleteAssignment: string;
  };
  review: {
    status: {
      DRAFT: string;
      SELF_PENDING: string;
      SELF_SUBMITTED: string;
      MANAGER_PENDING: string;
      MANAGER_SUBMITTED: string;
      CALIBRATION: string;
      FINALIZED: string;
      APPEAL_REQUESTED: string;
      APPEAL_RESOLVED: string;
      ARCHIVED: string;
    };
    field: {
      kpiScore: string;
      finalScore: string;
      finalGrade: string;
    };
    action: {
      transition: string;
    };
    kpi: {
      empty: string;
      col: {
        node: string;
        weight: string;
        target: string;
        actual: string;
        achievementRate: string;
        autoScore: string;
        managerScore: string;
        itemScore: string;
      };
    };
    self: {
      title: string;
      description: string;
      employeeId: string;
      employeeIdPlaceholder: string;
      load: string;
      needInput: string;
      empty: string;
      emptyHint: string;
      reviewTitle: string;
      kpiSection: string;
      selfComment: string;
      selfCommentPlaceholder: string;
      saveDraft: string;
      submit: string;
      submitted: string;
      confirmSubmit: string;
      lockedHint: string;
    };
    manager: {
      title: string;
      description: string;
      create: string;
      needCycle: string;
      empty: string;
      col: {
        employeeId: string;
        status: string;
        kpiScore: string;
        finalScore: string;
      };
      tabScore: string;
      tabCompare: string;
      managerComment: string;
      managerCommentPlaceholder: string;
      saveDraft: string;
      submit: string;
      submitted: string;
      previewKpiScore: string;
      previewHint: string;
      notEditableHint: string;
    };
    create: {
      title: string;
      modeSingle: string;
      modeBulk: string;
      employeeId: string;
      employeeIdPlaceholder: string;
      employeeIds: string;
      employeeIdsHint: string;
      employeeIdsPlaceholder: string;
      needEmployeeId: string;
      needEmployeeIds: string;
      bulkResult: string;
      note: string;
    };
    compare: {
      self: string;
      manager: string;
      noComment: string;
      scoreCompare: string;
      autoScore: string;
      managerScore: string;
      delta: string;
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
    E9804443: string;
    E9804444: string;
    E9804445: string;
    E9804446: string;
    E9804236: string;
    E9804237: string;
    E9804238: string;
    E9804239: string;
    E9804924: string;
    E9804925: string;
    E9804926: string;
    E9804927: string;
    E9804447: string;
    E9804240: string;
    E9804241: string;
    E9804242: string;
    E9804243: string;
    E9804244: string;
    E9804245: string;
    E9804928: string;
    E9804929: string;
    E9804930: string;
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
    kpi: {
      my: '내 KPI',
      managerTree: 'KPI 트리 (매니저)',
      directorTree: 'KPI 트리 (본부)',
    },
    review: {
      self: '자기평가',
      manager: '매니저 평가',
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
  kpi: {
    selectCycle: '평가 사이클',
    noCycle: '사이클이 없습니다. 먼저 HR > 사이클 관리에서 생성하세요.',
    cyclePlaceholder: '사이클 선택',
    level: {
      CORPORATE: '전사',
      DIVISION: '본부',
      TEAM: '팀',
      INDIVIDUAL: '개인',
    },
    bscPerspective: {
      FINANCIAL: '재무',
      CUSTOMER: '고객',
      INTERNAL_PROCESS: '내부 프로세스',
      LEARNING_GROWTH: '학습과 성장',
      UNASSIGNED: '미지정',
    },
    source: {
      MANUAL: '수동',
      HCM: 'HCM 연동',
      EXTERNAL: '외부',
    },
    weightBadge: {
      complete: '가중치 합 100%',
      incomplete: '가중치 합 {sum}%',
      exceeded: '가중치 초과 {sum}%',
    },
    my: {
      title: '내 KPI',
      description: '배정된 KPI의 가중치·목표·최신 실적·달성률을 확인하고 실적을 입력하세요.',
      employeeId: '사원 ID',
      employeeIdPlaceholder: '사원 ID (UUID)',
      load: '조회',
      empty: '배정된 KPI가 없습니다',
      emptyHint: '선택한 사이클·사원에 배정된 KPI가 없습니다.',
      needInput: '사이클과 사원 ID를 입력한 뒤 조회하세요.',
      col: {
        node: 'KPI',
        tree: '트리',
        weight: '가중치',
        target: '목표',
        latestActual: '최신 실적',
        achievementRate: '달성률',
      },
      reportActual: '실적 입력',
      actualHistory: '실적 이력',
    },
    manager: {
      title: 'KPI 트리 (매니저)',
      description: '평가 사이클별 KPI 트리를 구성하고 노드·배정을 관리하세요.',
      treeList: 'KPI 트리',
      createTree: '트리 생성',
      empty: '이 사이클에는 KPI 트리가 없습니다',
      emptyTree: '노드가 없습니다. 최상위 노드를 추가하세요.',
      addRootNode: '최상위 노드 추가',
      addChild: '하위 노드 추가',
      editNode: '노드 수정',
      deleteNode: '노드 삭제',
      manageAssignments: '배정 관리',
      editTree: '트리 수정',
      deleteTree: '트리 삭제',
      childWeightSum: '자식 가중치 합',
    },
    director: {
      title: 'KPI 트리 (본부)',
      description: '읽기 전용 KPI 트리 — BSC 4 관점 그룹핑을 토글하세요.',
      bscToggle: 'BSC 4 관점 보기',
      readonly: '읽기 전용',
      empty: '이 사이클에는 KPI 트리가 없습니다',
    },
    tree: {
      name: '트리 이름',
      level: '레벨',
      ownerOrgUnitId: '소유 조직 ID',
      bscEnabled: 'BSC 4 관점 활성',
      create: '트리 생성',
    },
    node: {
      label: '레이블',
      weight: '가중치 (0~1)',
      target: '목표값',
      unit: '단위',
      bscPerspective: 'BSC 관점',
      source: '소스',
      cascadeFromId: '상위 트리 KPI 참조 ID',
      parent: '부모 노드',
      parentRoot: '(최상위)',
      assignments: '배정',
      assignmentCount: '배정 수',
      create: '노드 추가',
      weightHint: '0 초과 1 이하의 비율 (예: 0.25 = 25%)',
    },
    assignment: {
      title: '배정 관리',
      employeeId: '사원 ID',
      weight: '가중치 (개인 override)',
      weightOverrideHint: '비우면 노드 기본 가중치를 사용합니다.',
      targetOverride: '목표 override',
      add: '배정 추가',
      empty: '배정이 없습니다',
      effectiveWeight: '적용 가중치',
      effectiveTarget: '적용 목표',
    },
    actual: {
      title: '실적 입력',
      asOfDate: '기준일',
      actualValue: '실적값',
      evidenceUrl: '증빙 URL',
      comment: '비고',
      report: '실적 입력',
      history: '실적 이력',
      empty: '실적 기록이 없습니다',
      superseded: '정정됨',
      latest: '최신',
      correct: '정정',
      correctTitle: '실적 정정',
      reportedAt: '기록일시',
    },
    confirmDeleteNode: '이 노드를 삭제하시겠습니까? 자식 노드가 있으면 삭제할 수 없습니다.',
    confirmDeleteTree: '이 트리를 삭제하시겠습니까? 트리 내 모든 노드·배정·실적이 함께 삭제됩니다.',
    confirmDeleteAssignment: '이 배정을 삭제하시겠습니까? 관련 실적도 함께 삭제됩니다.',
  },
  review: {
    status: {
      DRAFT: '작성 전',
      SELF_PENDING: '자기평가 대기',
      SELF_SUBMITTED: '자기평가 제출',
      MANAGER_PENDING: '매니저 평가 대기',
      MANAGER_SUBMITTED: '매니저 평가 제출',
      CALIBRATION: 'Calibration',
      FINALIZED: '확정',
      APPEAL_REQUESTED: '이의신청',
      APPEAL_RESOLVED: '이의신청 처리',
      ARCHIVED: '보관됨',
    },
    field: {
      kpiScore: 'KPI 점수',
      finalScore: '최종 점수',
      finalGrade: '최종 등급',
    },
    action: {
      transition: '상태 전이',
    },
    kpi: {
      empty: 'KPI 항목이 없습니다.',
      col: {
        node: 'KPI',
        weight: '가중치',
        target: '목표',
        actual: '실적',
        achievementRate: '달성률',
        autoScore: '자동 점수',
        managerScore: '매니저 점수',
        itemScore: '항목 점수',
      },
    },
    self: {
      title: '자기평가',
      description: '배정된 KPI의 자체 점검 결과를 확인하고 자기평가 의견을 작성·제출하세요.',
      employeeId: '사원 ID',
      employeeIdPlaceholder: '사원 ID (UUID)',
      load: '조회',
      needInput: '사이클과 사원 ID를 입력한 뒤 조회하세요.',
      empty: '자기평가가 아직 생성되지 않았습니다',
      emptyHint: 'HR 또는 매니저가 평가를 생성하면 자기평가를 시작할 수 있습니다.',
      reviewTitle: '자기평가',
      kpiSection: 'KPI 자체 점검',
      selfComment: '자기평가 의견',
      selfCommentPlaceholder: '이번 평가 기간의 성과와 자기평가 의견을 작성하세요.',
      saveDraft: '임시 저장',
      submit: '제출',
      submitted: '자기평가를 제출했습니다.',
      confirmSubmit: '자기평가를 제출하시겠습니까? 제출 후에는 수정할 수 없습니다.',
      lockedHint: '이미 제출되었거나 자기평가 단계가 아니어서 읽기 전용입니다.',
    },
    manager: {
      title: '매니저 평가',
      description: '평가 사이클별 대상자 목록에서 KPI 채점과 자기·매니저 비교를 진행하세요.',
      create: '평가 생성',
      needCycle: '평가 사이클을 선택하세요.',
      empty: '이 사이클에는 평가가 없습니다',
      col: {
        employeeId: '사원 ID',
        status: '상태',
        kpiScore: 'KPI 점수',
        finalScore: '최종 점수',
      },
      tabScore: 'KPI 채점',
      tabCompare: '자기 ↔ 매니저 비교',
      managerComment: '매니저 의견',
      managerCommentPlaceholder: '대상자의 성과에 대한 매니저 의견을 작성하세요.',
      saveDraft: '임시 저장',
      submit: '제출',
      submitted: '매니저 평가를 제출했습니다.',
      previewKpiScore: 'KPI 점수 프리뷰',
      previewHint: '입력값 기준 가중 합산 미리보기입니다. 최종 점수는 제출 후 서버 계산값을 사용합니다.',
      notEditableHint: '매니저 평가 대기 상태가 아니어서 점수 입력이 비활성화되었습니다.',
    },
    create: {
      title: '평가 생성',
      modeSingle: '개별',
      modeBulk: '일괄',
      employeeId: '사원 ID',
      employeeIdPlaceholder: '사원 ID (UUID)',
      employeeIds: '사원 ID 목록',
      employeeIdsHint: '줄바꿈 또는 쉼표로 여러 사원 ID를 구분하세요. 이미 생성된 대상자는 자동으로 건너뜁니다.',
      employeeIdsPlaceholder: 'uuid-1\nuuid-2\nuuid-3',
      needEmployeeId: '사원 ID를 입력하세요.',
      needEmployeeIds: '사원 ID를 하나 이상 입력하세요.',
      bulkResult: '생성 {created}건 / 건너뜀 {skipped}건',
      note: '평가는 작성 전(DRAFT) 상태로 생성됩니다.',
    },
    compare: {
      self: '자기평가',
      manager: '매니저 평가',
      noComment: '작성된 의견이 없습니다.',
      scoreCompare: '항목별 점수 비교',
      autoScore: '자동 점수',
      managerScore: '매니저 점수',
      delta: '차이',
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
    E9804443: 'KPI 트리를 찾을 수 없습니다',
    E9804444: 'KPI 노드를 찾을 수 없습니다',
    E9804445: 'KPI 배정을 찾을 수 없습니다',
    E9804446: 'KPI 실적을 찾을 수 없습니다',
    E9804236: '부모 노드가 다른 트리에 속해 있습니다',
    E9804237: '가중치는 0 초과 1 이하여야 합니다',
    E9804238: '형제 노드의 가중치 합이 1.0 을 초과합니다',
    E9804239: 'P0 단계에서는 수동(MANUAL) 소스만 지원합니다',
    E9804924: '동일한 사원이 이미 이 노드에 배정되어 있습니다',
    E9804925: '이미 정정된 실적은 다시 정정할 수 없습니다',
    E9804926: '자식 노드가 있는 노드는 삭제할 수 없습니다',
    E9804927: '확정/취소된 사이클에서는 KPI를 변경할 수 없습니다',
    E9804447: '평가를 찾을 수 없습니다',
    E9804240: '허용되지 않은 상태 전이입니다',
    E9804241: '현재 사이클 단계에서는 이 전이를 진행할 수 없습니다',
    E9804242: '매니저 점수는 0 이상 100 이하여야 합니다',
    E9804243: '해당 평가 대상이 아닌 KPI 배정이 포함되어 있습니다',
    E9804244: 'KPI 점수가 없어 확정할 수 없습니다',
    E9804245: '현재 상태에서는 이 항목을 수정할 수 없습니다',
    E9804928: '동일한 대상자의 평가가 이미 존재합니다',
    E9804929: '제출되었거나 종결된 평가는 수정할 수 없습니다',
    E9804930: '작성 전(DRAFT) 상태의 평가만 삭제할 수 있습니다',
  },
};

export type I18nDict = I18nShape;
