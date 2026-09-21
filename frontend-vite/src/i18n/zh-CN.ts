import { zh_CN as sharedLabels } from '@easy/i18n-common/locales/zh_CN';
import { programZhCN } from '../features/evaluation-programs/programI18n.ts';
/**
 * 简体中文 i18n 资源。
 */
import type { I18nDict } from './ko';

export const zhCN: I18nDict = {
  program: programZhCN,
  common: {
    action: { create: sharedLabels['common.action.add'], save: sharedLabels['common.action.save'], cancel: sharedLabels['common.action.cancel'], edit: sharedLabels['common.action.edit'], delete: sharedLabels['common.action.delete'], submit: sharedLabels['common.action.submit'], close: sharedLabels['common.action.close'], retry: '重试' },
    status: { active: sharedLabels['common.status.active'], inactive: sharedLabels['common.status.inactive'], loading: sharedLabels['common.message.loading'], empty: sharedLabels['common.message.no_data'] },
    message: { created: '创建成功。', updated: sharedLabels['common.message.save_success'], deleted: '删除成功。', loadError: sharedLabels['common.message.load_failed'] },
    label: { darkMode: '深色模式', language: '语言', logout: '退出登录' },
  },
  nav: {
    cockpit: '绩效驾驶舱',
    hr: { cycles: '人力资源 > 评价周期' },
    kpi: { my: '我的 KPI', alignment: '目标对齐', managerTree: 'KPI 树（经理）', directorTree: 'KPI 树（总监）' },
    review: { self: '自我评价', manager: '经理评价' },
    calibration: { sessions: '校准会议', director: '校准（总监）', analytics: '校准分析', distribution: '分布模拟器' },
    report: { hr: '人力资源 > 报告', my: '我的结果' },
    admin: { tenants: '系统 > 租户' },
  },
  workspace: {
    navMy: '我的评价', navOperate: '评价运营', appTitle: 'Easy 绩效管理', appSubtitle: '从目标到反馈', cycle: '评价周期', preparing: '正在准备评价运营页面。',
    myEvaluation: '我的评价', operations: '运营管理', participants: '评价对象与评价人', participantSelect: '评价对象', reviewerSelect: '主要评价人', assign: '保存分配',
    progress: '进度摘要', achievements: '主要成果', blockers: '阻碍或所需支持', supportNeeded: '所需支持',
    goal: '目标', goalDescription: '成功标准或行动计划', weight: '权重（%）', saveDraft: '保存草稿', submit: '提交', open: '开启评价', next: '下一阶段', publish: '发布结果', close: '结束评价', acknowledge: '确认结果',
    setup: '运营准备', participantsPhase: '评价对象', goals: '目标共识', checkIn: '中期检查', selfReview: '自我评价', teamReview: '团队评价', calibration: '校准', results: '结果与反馈', closure: '结束',
    copy: {
      mobileAppTitle: '绩效', noAssignedCycleTitle: '暂无已分配的评价', noAssignedCycleDescription: '人力资源开启并分配评价后，将显示在这里。', setupDescription: '确认评价期间和运营政策。', participantsDescription: '分配评价对象及其主要评价人。', goalsDescription: '成员填写目标，由经理审批。', checkInDescription: '检查进度和所需支持。', selfReviewPhaseDescription: '成员整理成果和依据。', teamReviewDescription: '评价人评价已分配的成员。', calibrationDescription: '调整等级分布并复核评价结果。', resultsDescription: '查看已发布的结果和反馈。', closureDescription: '结束前确认结果已发布且反馈已完成。', currentPhase: '当前阶段', noCycleTitle: '当前没有运行中的评价周期', noCycleDescription: '创建评价周期后，可在此管理参与者和各阶段。', createCycle: '创建评价周期', loadingMyWork: '正在加载我的评价任务。', blockersTitle: '进入下一阶段前请处理以下项目', operationDescription: '服务器会根据完成条件和阻碍项目判断能否进入下一阶段。', calibrationOpen: '打开个人校准', employeeNamePlaceholder: '按姓名或员工编号搜索', noParticipantsTitle: '尚未分配评价对象', noParticipantsDescription: '开启评价前，请分配成员及其主要评价人。', member: '成员', org: '组织', reviewStatus: '评价状态', unassigned: '未分配', notSpecified: '未指定', noTask: '本周期没有分配给您的评价任务。', goalWrite: '填写目标', checkInDescriptionShort: '记录目标进度和反馈，而非考勤。', selfReviewDescription: '主要成果和下一步成长计划', resultsFeedback: '结果与反馈', notCalculated: '尚未计算', acknowledged: '您已确认评价结果。', acknowledgeHint: '查看结果后，确认已收到。', appeal: '申诉', appealPlaceholder: '请说明需要复核的原因。', teamLoading: '正在加载团队评价任务。', noTeamTitle: '暂无已分配的团队评价', noTeamDescription: '目标共识、中期检查和最终评价任务会显示在这里。', reviewerFeedback: '中期检查反馈', reviewerFeedbackPlaceholder: '请填写指导意见和所需支持。', completeCheckIn: '完成中期检查', teamScore: '团队评价分数', loadingItems: '正在加载评价项目。', reviewComment: '评价意见', submitTeamReview: '提交团队评价', feedbackSave: '保存反馈', feedbackSubmitted: '反馈已保存。',
      statusEmpty: '未开始', status_ACTIVE: '进行中', status_EXCLUDED: '已排除', status_DRAFT: '草稿', status_PENDING_APPROVAL: '等待审批', status_APPROVED: '已批准', status_REJECTED: '要求修改', status_EMPLOYEE_SUBMITTED: '成员已提交', status_MANAGER_COMPLETED: '经理已完成', status_SELF_PENDING: '等待自我评价', status_SELF_SUBMITTED: '自我评价已提交', status_MANAGER_PENDING: '等待经理评价', status_MANAGER_SUBMITTED: '经理评价已提交', status_CALIBRATION: '校准', status_FINALIZED: '已定稿', status_PLANNED: '计划中', status_GOAL_SETTING: '目标设定', status_MID_REVIEW: '中期检查', status_SELF_REVIEW: '自我评价', status_MANAGER_REVIEW: '团队评价', status_IN_SESSION: '进行中', status_CONFIRMED: '已确认', status_COMPLETED: '反馈已完成', status_ACCEPTED: '已接受', status_APPEALED: '已申诉', status_RESOLVED: '已解决',
      openConfirmTitle: '开启此评价？', openConfirmBody: '成员将看到此评价，并可开始确认目标。', opened: '评价已开启。', noEmployeeMatches: '没有符合搜索条件的员工。', advanced: '已进入下一阶段。', published: '结果已发布。', closed: '评价已结束。', assigned: '参与者和评价人已保存。', peopleSuffix: ' 人', assignmentHint: '所选主要评价人将应用于所选成员。', reviewerPlaceholder: '选择评价人', loadingParticipants: '正在加载参与者。',
      nextWork: '下一项工作', nextWorkReady: '下一项任务已就绪。', pendingApprovalSuffix: ' 项等待审批', goalCountSuffix: ' 个目标', reviewPersonSuffix: ' 人待评价', loadingGoals: '正在加载目标。', noGoals: '尚未提交目标。', target: '目标值', requestRevision: '要求修改', approve: '批准', goalDecisionComment: '目标审批意见', goalDecisionPlaceholder: '要求修改时必须填写具体原因。', goalRejected: '已要求修改目标。', goalApproved: '目标已批准。', feedbackSaved: '反馈已保存。', checkInCompleted: '中期检查已完成。', noReviewItems: '暂无可评价项目。', actual: '实际值', autoScore: '自动评分', managerScore: '经理评分', managerReviewSaved: '经理评价草稿已保存。', managerReviewSubmitted: '经理评价已提交。', scoreRequiredHint: '提交前请为每个项目填写分数。',
      goalEdit: '编辑目标', targetValue: '目标值', unit: '单位', revisionReason: '修改要求', goalUpdated: '目标已更新。', saveChanges: '保存修改', goalSubmitted: '已申请审批。', requestApproval: '申请审批', noDescription: '暂无说明', noCheckIns: '暂无进度记录。', asOfDate: '基准日期', actualValue: '实际值', progressPercent: '进度（%）', note: '进度说明', addCheckIn: '添加进度记录', evidenceUrl: '依据链接', checkInAdded: '进度已记录。', saveCheckIn: '保存进度', goalCreated: '目标已保存。', saveGoal: '保存目标', checkInSaved: '中期检查草稿已保存。', checkInSubmitted: '中期检查已提交。', submitToManager: '提交给经理', selfReviewSaved: '自我评价草稿已保存。', selfReviewSubmitted: '自我评价已提交。', finalScore: '最终分数', finalGrade: '等级', resultAcknowledged: '已确认收到结果。', feedbackFromManager: '经理反馈', feedbackAccepted: '反馈已接受。', acceptFeedback: '接受反馈', appealSubmitted: '申诉已提交。', feedbackStatus: '反馈状态',
      loadingCalibration: '正在加载校准任务。', distributionTotal: '目标合计', noCalibrationSession: '暂无校准会议', noCalibrationSessionHint: '为所有有效参与者创建校准会议。', createCalibrationSession: '创建会议', calibrationSessionCreated: '校准会议已创建。', calibrationSession: '校准会议', currentDistribution: '当前分布', distributionApplied: '目标分布已应用。', applyDistribution: '应用分布', noCalibrationRows: '暂无可进行校准的评价。', reviewSubject: '评价对象', adjustedGrade: '调整后等级', adjustmentReason: '调整原因', adjustmentSaved: '调整结果和原因已保存。', saveAdjustment: '保存调整', calibrationConfirmed: '校准已确认，已定稿', countSuffix: ' 项评价', confirmCalibration: '确认校准',
      loadingResults: '正在加载结果分析。', resultsAnalysis: '结果分析', finalized: '已定稿', averageScore: '平均分', gradeDistribution: '等级分布', companyWide: '全公司', noResultRows: '暂无结果数据。', loadingFeedback: '正在加载反馈任务。', feedbackOperations: '反馈与申诉', appealReason: '申诉原因', feedbackComment: '反馈意见', feedbackCompleted: '反馈已完成。', completeFeedback: '完成反馈', resolution: '处理结果', resolutionUpheld: '维持原结果', resolutionAdjustment: '需要调整', resolutionComment: '处理意见', appealResolved: '申诉已解决。', resolveAppeal: '处理申诉', noFeedbackAction: '此反馈暂无可用操作。',
      error_E9804253: '您的账户尚未关联员工档案，请联系 HR 完成关联。', error_E9804301: '您没有执行此评价任务的权限。', error_E9804450: '找不到评价参与者。', error_E9804451: '找不到指定的评价人。', error_E9804452: '反馈尚未创建。', error_E9804254: '请选择有效员工和评价人。', error_E9804935: '进入下一阶段前，请完成列出的要求。', error_E9804936: '此反馈已完成，无法继续编辑。', error_E9804252: '当前阶段无法发布结果。', unknownError: '请求未能完成，请重试。',
    },
  },
  cockpit: {
    title: '绩效驾驶舱', description: '在一个页面跟踪评价周期、评价进度、校准准备度和报告发布情况。', empty: '暂无进行中的评价周期', emptyDescription: '创建周期后，即可自动汇总评价、校准和报告。',
    stat: { cycles: '评价周期', cyclesHint: '基于近期周期的运营视图', reviewProgress: '评价进度', reviewProgressHint: '{done}/{total} 项评价已提交或进入后续阶段', calibrationReady: '校准就绪', calibrationReadyHint: '{total} 项评价中已就绪的数量', reports: '报告', reportsHint: '{finalized} 项已定稿评价中已发布的报告' },
    pipeline: { title: '评价流程', description: '所选周期各状态的分布。' },
    distribution: { title: '等级分布', description: '按校准与分布接口返回的数据原样显示。', current: '当前分布', total: '共 {count} 项', empty: '暂无分布数据。' },
    cycles: { title: '近期周期', description: '查看当前运营周期及相邻周期的状态。', policy: '政策' },
  },
  login: {
    personaLabel: '试用演示账户', personaHint: '选择角色后会自动填写开发环境的种子账户（需启用种子数据开关）', tenantCode: '公司代码', tenantCodePlaceholder: '公司代码（可选）', emailLabel: sharedLabels['common.label.email'], passwordLabel: '密码', submit: '登录',
    persona: { superAdmin: '系统管理员', hrAdmin: '人力资源', director: '总监', manager: '经理', employee: '员工' },
  },
  adminTenants: {
    title: '租户管理', description: '系统管理员可创建、预配、暂停和恢复控制平面的租户。', gateOffTitle: '多租户开关已关闭（单数据库模式）', gateOffBody: '控制平面尚未连接，因此租户生命周期接口返回 503。启用 APP_NEON_MULTITENANCY_ENABLED 并配置控制平面环境后，本页面会自动显示实时数据。', create: '创建租户', empty: '暂无租户', emptyHint: '点击创建按钮登记首个客户，Neon 预配会立即开始。', createTitle: '创建租户', createHint: '创建时会在控制平面登记租户、初始化 PERFORMANCE 订阅，并在后台执行 Neon 预配（通常需要数十秒）。列表会自动跟踪“预配中 → 已启用”的状态。', createFailed: '创建失败，请检查代码是否重复以及输入值是否正确。',
    field: { code: '代码', name: '名称', region: '区域', adminUsername: '管理员用户名', adminEmail: '管理员电子邮箱' }, column: { code: '代码', name: '名称', status: '状态', region: '区域', neonProject: 'Neon 项目', admin: '管理员', createdAt: '创建时间' }, action: { retry: '重试', suspend: '暂停', resume: '恢复', cancel: '取消', create: '创建' },
  },
  cycles: {
    title: '评价周期', create: '创建周期', empty: '暂无评价周期', policy: { notSet: '未设置政策' }, action: { edit: '编辑', transition: '变更状态', policy: '编辑政策', delete: '删除' }, field: { name: '名称', periodStart: '开始日期', periodEnd: '结束日期', cycleType: '周期类型', status: '状态' }, type: { HALF_ANNUAL: '半年度', ANNUAL: '年度', QUARTERLY: '季度', MONTHLY: '月度', CUSTOM: '自定义' }, status: { PLANNED: '计划中', ACTIVE: '进行中', GOAL_SETTING: '目标设定', MID_REVIEW: '中期评价', SELF_REVIEW: '自我评价', MANAGER_REVIEW: '经理评价', CALIBRATION: '校准', FINALIZED: '已定稿', CANCELLED: '已取消' },
    operating: { total: '评价周期', totalHint: '已登记的评价周期', policyReady: '政策就绪', inOperation: '运营中', inOperationHint: '尚未定稿或取消的周期', transitionable: '可变更状态', transitionableHint: '存在可用下一状态的周期', policyProgress: '政策准备度', timelineTitle: '周期状态时间线', timelineDescription: '查看评价周期在各运营阶段的分布。' },
  },
  policy: {
    title: '评价政策', field: { distributionMode: '分布模式', ratingScale: '评级量表', appealEnabled: '启用申诉', bscEnabled: '启用 BSC 四个维度', achievementLogCutoffDays: '评价后报告截止天数', forcedDistribution: '强制分布' }, distributionMode: { HYBRID: '混合', FORCED: '强制', ABSOLUTE: '绝对' }, ratingScale: { S_A_B_C_D: 'S/A/B/C/D', ONE_TO_FIVE: '1 至 5', ONE_TO_HUNDRED: '1 至 100' }, distributionSum: '合计', distributionSumMustBeOne: '分布合计必须为 1.0（当前：{sum}）',
  },
  kpi: {
    selectCycle: '评价周期', noCycle: '暂无周期，请先在人力资源 > 评价周期中创建。', cyclePlaceholder: '选择周期',
    level: { CORPORATE: '公司', DIVISION: '事业部', TEAM: '团队', INDIVIDUAL: '个人' },
    bscPerspective: { FINANCIAL: '财务', CUSTOMER: '客户', INTERNAL_PROCESS: '内部流程', LEARNING_GROWTH: '学习与成长', UNASSIGNED: '未分配' },
    source: { MANUAL: '手动', HCM: 'HCM', EXTERNAL: '外部' },
    weightBadge: { complete: '权重合计 100%', incomplete: '权重合计 {sum}%', exceeded: '权重超出 {sum}%' },
    my: {
      title: '我的 KPI', description: '查看已分配 KPI 的权重、目标、最新实际值和达成率，并上报实际值。', employeeId: '员工 ID', employeeIdPlaceholder: '员工 ID（UUID）', load: '加载', empty: '暂无已分配的 KPI', emptyHint: '所选周期和员工暂无已分配的 KPI。', needInput: '输入周期和员工 ID 后加载。',
      col: { node: 'KPI', tree: 'KPI 树', weight: '权重', target: '目标值', latestActual: '最新实际值', achievementRate: '达成率' }, reportActual: '上报实际值', actualHistory: '实际值历史',
    },
    alignment: {
      title: '目标对齐', description: '比较组织 KPI 树与个人 KPI 分配及实际值。', tree: '组织 KPI 树', treePlaceholder: '选择要比较的 KPI 树', needCycle: '选择周期后加载组织 KPI 树。', empty: '暂无可比较的 KPI 数据', emptyDescription: '请先创建组织 KPI 树或个人 KPI 分配。', treePanel: '组织目标', personalPanel: '个人分配', personalPanelDescription: '检查个人 KPI 分配是否连接到所选组织 KPI 树。', orgMatch: '组织匹配', matched: '已匹配', unmatched: '未匹配',
      stat: { aligned: '已匹配 KPI', alignedHint: '连接到所选 KPI 树的个人分配', coverage: '对齐率', coverageHint: '基于 {tree}', avgAchievement: '平均达成率', avgAchievementHint: '已有实际值的个人 KPI 平均值', unmatched: '未匹配', unmatchedHint: '不在所选组织 KPI 树中的个人分配' },
    },
    manager: {
      title: 'KPI 树（经理）', description: '按评价周期构建 KPI 树，并管理节点和分配。', treeList: 'KPI 树', createTree: '创建 KPI 树', empty: '本周期暂无 KPI 树', emptyTree: '暂无节点，请添加根节点。', addRootNode: '添加根节点', addChild: '添加子节点', editNode: '编辑节点', deleteNode: '删除节点', manageAssignments: '管理分配', editTree: '编辑 KPI 树', deleteTree: '删除 KPI 树', childWeightSum: '子节点权重合计',
    },
    director: {
      title: 'KPI 树（总监）', description: '只读 KPI 树，可切换 BSC 四维度分组。', bscToggle: 'BSC 四维度视图', readonly: '只读', empty: '本周期暂无 KPI 树',
      operating: { trees: 'KPI 树', treesHint: '本周期的组织 KPI 组合', bscCoverage: 'BSC 覆盖率', orgScoped: '限定组织', orgScopedHint: '已指定负责组织单位的 KPI 树', levels: '层级', levelsHint: '组合中包含的 KPI 树层级', bscProgress: 'BSC 覆盖进度', nodes: 'KPI 节点', nodesHint: '所选 KPI 树中的项目', bscAssigned: '已分配维度', weightComplete: '权重完整', assignments: '分配', assignmentsHint: '各节点的评价对象分配总数', weightProgress: '权重完成进度' },
    },
    tree: { name: 'KPI 树名称', level: '层级', ownerOrgUnitId: '负责组织单位 ID', bscEnabled: '启用 BSC 四个维度', create: '创建 KPI 树' },
    node: { label: '名称', weight: '权重（0–1）', target: '目标值', unit: '单位', bscPerspective: 'BSC 维度', source: '来源', cascadeFromId: '级联来源 KPI ID', parent: '父节点', parentRoot: '（根节点）', assignments: '分配', assignmentCount: '分配数', create: '添加节点', weightHint: '请输入大于 0 且不超过 1 的比例（例如 0.25 = 25%）' },
    assignment: { title: '管理分配', employeeId: '员工 ID', weight: '权重（覆盖值）', weightOverrideHint: '留空时使用节点的默认权重。', targetOverride: '目标覆盖值', add: '添加分配', empty: '暂无分配', effectiveWeight: '有效权重', effectiveTarget: '有效目标值' },
    actual: { title: '上报实际值', asOfDate: '基准日期', actualValue: '实际值', evidenceUrl: '依据链接', comment: '备注', report: '上报实际值', history: '实际值历史', empty: '暂无实际值记录', superseded: '已被替代', latest: '最新', correct: '更正', correctTitle: '更正实际值', reportedAt: '上报时间' },
    confirmDeleteNode: '删除此节点？包含子节点的节点无法删除。', confirmDeleteTree: '删除此 KPI 树？其中所有节点、分配和实际值都会被删除。', confirmDeleteAssignment: '删除此分配？关联的实际值也会被删除。',
  },
  review: {
    status: { DRAFT: '草稿', SELF_PENDING: '等待自我评价', SELF_SUBMITTED: '自我评价已提交', MANAGER_PENDING: '等待经理评价', MANAGER_SUBMITTED: '经理评价已提交', CALIBRATION: '校准', FINALIZED: '已定稿', APPEAL_REQUESTED: '已申请申诉', APPEAL_RESOLVED: '申诉已解决', ARCHIVED: '已归档' },
    field: { kpiScore: 'KPI 分数', finalScore: '最终分数', finalGrade: '最终等级' }, action: { transition: '变更状态' },
    kpi: { empty: '暂无 KPI 项目。', col: { node: 'KPI', weight: '权重', target: '目标值', actual: '实际值', achievementRate: '达成率', autoScore: '自动评分', managerScore: '经理评分', itemScore: '项目分数' } },
    self: {
      title: '自我评价', description: '查看已分配 KPI 的自动核算结果，填写并提交自我评价。', employeeId: '员工 ID', employeeIdPlaceholder: '员工 ID（UUID）', load: '加载', needInput: '选择周期并输入员工 ID 后加载。', empty: '尚未创建自我评价', emptyHint: '人力资源或经理创建评价后，即可开始自我评价。', reviewTitle: '自我评价', kpiSection: 'KPI 自检', selfComment: '自我评价意见', selfCommentPlaceholder: '请说明本期间的主要成果和自我评价。', saveDraft: '保存草稿', submit: '提交', submitted: '自我评价已提交。', confirmSubmit: '提交自我评价？提交后将无法编辑。', lockedHint: '当前为只读状态：评价已提交，或尚未进入自我评价阶段。',
    },
    manager: {
      title: '经理评价', description: '从各周期的评价对象列表中为 KPI 评分，并比较员工与经理的评价。', create: '创建评价', needCycle: '请选择评价周期。', empty: '本周期暂无评价',
      col: { employeeId: '员工 ID', status: '状态', kpiScore: 'KPI 分数', finalScore: '最终分数' }, tabScore: 'KPI 评分', tabCompare: '员工 ↔ 经理', managerComment: '经理意见', managerCommentPlaceholder: '请填写经理对评价对象绩效的意见。', saveDraft: '保存草稿', submit: '提交', submitted: '经理评价已提交。', previewKpiScore: 'KPI 分数预览', previewHint: '根据当前输入按权重计算的预览值。最终分数以提交后的服务器计算结果为准。', notEditableHint: '当前评价不处于等待经理评价状态，因此无法输入分数。',
      workspace: { queue: '评价队列', queueHint: '当前周期的所有评价对象', managerPending: '等待评分', managerPendingHint: '需要经理填写的评价', submitted: '已提交或后续阶段', avgKpi: 'KPI 平均分', avgKpiHint: '基于服务器计算的 KPI 分数', progress: '经理评价进度', reviewee: '评价对象', revieweeHint: '当前选择的评价对象', serverScoreHint: '服务器计算值', noGrade: '暂无等级', finalizedAt: '定稿时间', finalized: '已定稿', notFinalized: '尚未定稿', scoredItems: '已评分项目', scoredItemsHint: '已填写经理评分的 KPI 项目', scoringProgress: '评分进度' },
    },
    create: {
      title: '创建评价', modeSingle: '单个', modeBulk: '批量', employeeId: '员工 ID', employeeIdPlaceholder: '员工 ID（UUID）', employeeIds: '员工 ID 列表', employeeIdsHint: '用换行或逗号分隔多个员工 ID。系统会自动跳过已有评价的员工。', employeeIdsPlaceholder: 'uuid-1\nuuid-2\nuuid-3', needEmployeeId: '请输入员工 ID。', needEmployeeIds: '请至少输入一个员工 ID。', bulkResult: '已创建 {created} 项／跳过 {skipped} 项', note: '评价将以草稿状态创建。',
    },
    compare: { self: '员工', manager: '经理', noComment: '尚未填写意见。', scoreCompare: '各项目分数比较', autoScore: '自动评分', managerScore: '经理评分', delta: '差值' },
  },
  calibration: {
    status: { PLANNED: '计划中', IN_SESSION: '进行中', ADJUSTED: '已调整', CONFIRMED: '已确认', CLOSED: '已关闭' }, grade: { UNRATED: '未评级' }, action: { transition: '变更状态', confirm: '确认', edit: '编辑', delete: '删除' },
    page: {
      title: '校准会议', description: '按周期创建并运行校准会议，然后确认等级调整。', create: '创建会议', needCycle: '请选择评价周期。', empty: '本周期暂无校准会议', companyWide: '全公司', confirmDelete: '删除此会议？只有计划中的会议可以删除。',
      col: { status: '状态', scheduledAt: '计划时间', ownerOrgUnit: '负责组织', participants: '参与者', adjustments: '调整数' },
    },
    form: {
      createTitle: '创建会议', editTitle: '编辑会议', ownerOrgUnitId: '负责组织单位 ID', ownerOrgUnitIdHint: '留空时创建全公司校准会议。', ownerOrgUnitIdPlaceholder: '组织单位 ID（UUID，可选）', scheduledAt: '计划时间', scheduledAtPlaceholder: '计划的校准会议时间', participantIds: '参与者 ID', participantIdsHint: '输入参与员工 ID（UUID）并按回车键添加。', participantIdsPlaceholder: '输入员工 ID 后按回车键', createNote: '会议将以计划中状态创建。', editNote: '只有计划中的会议可以编辑。',
    },
    confirm: {
      title: '确认会议', description: '确认此校准会议？确认后将无法更改等级调整。', finalizeReviews: '评价定稿', finalizeReviewsHint: '将校准阶段的所有评价转为已定稿。没有 KPI 分数的评价会被跳过。', finalizeWarning: '评价定稿后无法撤销，分数和等级将被锁定。', resultConfirmed: '会议已确认。', resultFinalized: '会议已确认。已定稿 {finalized} 项／跳过 {skipped} 项', submit: '确认',
    },
    adjust: { move: '调整等级', selectGrade: '目标等级', title: '调整等级', reason: '调整原因', reasonPlaceholder: '请输入本次等级调整的原因。', apply: '调整', done: '等级已调整。' },
    director: {
      title: '总监校准', description: '查看分布，并选择会议调整评价对象的等级。', needCycle: '请选择评价周期。', selectSession: '校准会议', sessionPlaceholder: '选择会议', noSession: '本周期暂无校准会议，请先由人力资源创建。', notAdjustableHint: '只有进行中或已调整的会议可以调整等级。', reviewsHeading: '评价对象等级', emptyReviews: '本周期暂无评价', logHeading: '调整历史', logEmpty: '暂无调整历史。',
      col: { employeeId: '员工 ID', status: '状态', kpiScore: 'KPI 分数', grade: '有效等级' },
    },
    analytics: {
      title: '校准分析', description: '在一个工作区查看会议状态、等级分布和调整历史。', needCycle: '选择评价周期后查看校准分析。', distributionTitle: '等级分布', distributionDescription: '比较当前分布和目标分布。', sessionsTitle: '会议状态', sessionsDescription: '本周期已登记的校准会议。', timelineTitle: '调整历史', timelineDescription: '所选会议的等级调整记录。',
      stat: { sessions: '会议', sessionsHint: '本周期的校准会议', ready: '校准就绪', readyHint: '{total} 项评价中符合条件的数量', adjustments: '调整', adjustmentsHint: '所有会议中的调整记录总数', forced: '强制分布', forcedHint: '分布应用状态' }, timelineCol: { when: '时间', employee: '员工', grade: '等级变化', reason: '原因' },
    },
  },
  distribution: {
    bars: { heading: '等级分布', current: '当前', target: '目标', totalCount: '共 {count} 项', targetMarker: '目标 {ratio}' },
    policy: { mode: '分布模式', scale: '评级量表', applied: '已应用强制分布', notApplied: '未应用', ready: '符合条件' },
    page: { title: '分布模拟器', description: '模拟强制分布，并批量应用到评价对象的等级。', needCycle: '请选择评价周期。', notSupported: '当前政策（绝对评价模式或非 S/A/B/C/D 量表）不支持强制分布。' },
    action: { simulate: '模拟', apply: '强制应用' },
    apply: { title: '应用强制分布', warning: '强制应用会批量更改评价对象的等级，请谨慎操作。', confirmText: '将 {count} 名评价对象的等级调整为符合目标分布，是否继续？', result: '已应用 {applied} 项／跳过 {skipped} 项' },
    proposed: { heading: '模拟结果', empty: '暂无强制分布目标（没有 KPI 分数且校准就绪的评价）。', changed: '已更改', col: { employeeId: '员工 ID', kpiScore: 'KPI 分数', currentGrade: '当前等级', proposedGrade: '建议等级' } },
    log: { heading: '应用历史', applied: '已应用 {count} 项', skipped: '已跳过 {count} 项' },
  },
  report: {
    hr: {
      title: '报告', description: '为已定稿的评价批量发布结果报告，并跟踪查看和确认状态。', needCycle: '请选择评价周期。', needFinalized: '只有已定稿周期才能发布报告。', summaryFinalized: '已定稿评价', summaryPublished: '已发布', publish: '全部发布', publishTitle: '批量发布报告', publishConfirm: '向 {count} 项已定稿评价中尚未收到报告的员工发布报告，是否继续？', publishResult: '已发布 {published} 项／跳过 {skipped} 项', empty: '尚未发布报告', emptyHint: '周期定稿后，可批量发布并生成报告。', viewed: '已查看', notViewed: '未查看', acknowledged: '已确认', notAcknowledged: '未确认', superseded: '已重新发布', supersede: '重新发布', supersedeTitle: '重新发布报告', supersedeWarning: '重新发布会根据当前定稿值和最新分布生成新的冻结报告，原报告将作为历史记录保留。', supersedeDone: '报告已重新发布。',
      governance: { title: '报告管理', description: '统一跟踪发布准备度、查看、确认和重新发布历史。', readiness: '可发布', readinessHint: '基于 {count} 项已定稿评价', published: '有效报告', viewed: '查看率', acknowledged: '确认率', superseded: '历史报告', progressPublished: '发布进度', progressViewed: '查看进度', progressAcknowledged: '确认进度', listTitle: '有效报告', listDescription: '当前对员工生效的最新报告。', historyTitle: '重新发布历史', historyDescription: '被重新发布替换的原报告会保留供审计。', historyEmpty: '暂无重新发布历史' },
      col: { employeeId: '员工 ID', finalGrade: '最终等级', publishedAt: '发布时间', status: '状态' },
    },
    my: { title: '我的结果', description: '查看已定稿的结果报告并确认收到。', employeeId: '员工 ID', employeeIdPlaceholder: '员工 ID（UUID）', load: '加载', needInput: '输入周期和员工 ID 后加载。', empty: '报告尚未发布', emptyHint: '人力资源发布结果报告后，您可在此查看。', acknowledgeDone: '已确认收到结果。' },
    card: { finalGrade: '最终等级', finalScore: '最终分数', kpiScore: 'KPI 分数', mboScore: 'MBO 分数', competencyScore: '胜任力分数', mraScore: '多评价人分数', scoreP1: '即将推出', kpiSection: 'KPI 项目摘要', managerComment: '经理意见', noComment: '暂无意见。', distributionHeading: '全公司等级分布', distributionHint: '报告发布时全公司的已定稿等级分布（比例），您的等级会突出显示。', distributionEmpty: '暂无分布数据。', distributionMine: '我的等级', acknowledgeHeading: '确认结果', acknowledgeHint: '记录您已查看结果。确认后无法更改。', acknowledge: '确认', acknowledgedButton: '已确认', acknowledged: '已确认' },
    packet: { title: '结果资料', description: '查看报告发布时冻结的最终等级、分数和生命周期状态。', publishedAt: '发布于 {date}', kpiItems: '{count} 个 KPI 项目', viewStatus: '查看状态', viewed: '已查看', notViewed: '正在记录查看状态', ackStatus: '确认状态', acknowledged: '已确认', notAcknowledged: '需要确认', lifecyclePending: '尚未记录', scoreTitle: '分数明细', scoreDescription: '最终分数中包含的只读绩效维度。', kpiDescription: '已定稿报告中包含的 KPI 项目快照。', commentDescription: '报告定稿时冻结的经理意见。', developmentTitle: '发展行动', developmentDescription: '跟踪评价结果后的发展任务和下次检查。', developmentEmpty: '暂无发展行动', developmentEmptyDescription: '连接后会在此显示发展目标、导师反馈和学习建议。' },
  },
  domain: {
    app: { title: 'easy-performance-management', subtitle: '绩效管理' },
    nav: { section: '绩效', selfEvaluation: '自我评价', personalOkr: '个人 OKR', reflectionJournal: '复盘日志', mentorFeedback: '导师反馈' },
    selfEvaluation: { title: '自我评价', description: '季度／年度自我评价的草稿与提交', empty: '暂无自我评价', emptyDescription: '创建新的自我评价，开始本季度评价。', period: '期间', content: '内容', score: '自评分', status: '状态', statusDraft: '草稿', statusSubmitted: '已提交', statusReviewed: '已审核', statusFinalized: '已定稿' },
    personalOkr: { title: '个人 OKR', description: '季度目标与关键结果（OKR）', empty: '暂无 OKR', emptyDescription: '在季度开始时添加 OKR，以跟踪个人目标。', objective: '目标', progress: '进度', period: '期间', status: '状态', statusActive: '进行中', statusAtRisk: '存在风险', statusCompleted: '已完成', statusArchived: '已归档' },
    reflectionJournal: { title: '复盘日志', description: '使用 KPT／4Ls／SSC 方法进行复盘', empty: '暂无复盘记录', emptyDescription: '选择一种方法，填写第一篇复盘。', reflectionDate: '复盘日期', method: '方法', content: '内容', isPrivate: '仅自己可见', methodKpt: 'KPT（保持／问题／尝试）', methodFourLs: '4Ls（喜欢／学到／欠缺／期待）', methodSsc: 'SSC（停止／开始／继续）' },
    mentorFeedback: { title: '导师反馈', description: '经理与团队成员的一对一反馈（成长／认可／辅导／沟通）', empty: '暂无反馈', emptyDescription: '记录第一次一对一反馈，支持团队成长。', feedbackDate: '反馈日期', mentor: '导师', mentee: '被辅导人', category: '类别', content: '内容', acknowledged: '已确认', categoryGrowth: '成长／学习', categoryRecognition: '认可／优势', categoryCoaching: '辅导／改进', categoryConversation: '一对一沟通' },
  },
  error: {
    boundary: '页面渲染错误', unknown: '发生未知错误。', network: '发生网络错误。', unauthorized: '请先登录。', forbidden: '权限不足。',
    E9804441: '找不到评价周期', E9804231: '状态转换无效', E9804921: '已存在同名评价周期', E9804232: '评价期间无效（结束日期必须晚于开始日期）', E9804922: '进行中或后续状态的周期无法删除', E9804442: '找不到评价政策', E9804233: '分布合计必须为 1.0', E9804234: '分布等级与评级量表不一致', E9804235: '强制分布模式必须设置分布比例', E9804923: '周期启用后无法更改分布模式或评级量表', E9804443: '找不到 KPI 树', E9804444: '找不到 KPI 节点', E9804445: '找不到 KPI 分配', E9804446: '找不到 KPI 实际值', E9804236: '父节点属于其他 KPI 树', E9804237: '权重必须大于 0 且不超过 1', E9804238: '同级节点的权重合计超过 1.0', E9804239: '当前版本仅支持手动来源', E9804924: '此员工已分配到该节点', E9804925: '已被替代的实际值无法再次更正', E9804926: '包含子节点的节点无法删除', E9804927: '已定稿或取消的周期无法更改 KPI', E9804447: '找不到评价', E9804240: '状态转换无效', E9804241: '当前周期阶段不允许此状态转换', E9804242: '经理评分必须介于 0 和 100 之间', E9804243: '包含了不属于此评价的 KPI 分配', E9804244: '缺少 KPI 分数，无法定稿', E9804245: '当前状态下无法编辑此部分', E9804928: '同一评价对象的评价已存在', E9804929: '已提交或关闭的评价无法编辑', E9804930: '只有草稿状态的评价可以删除', E9804448: '找不到校准会议', E9804246: '会议状态转换无效（确认只能通过确认操作完成）', E9804247: '目标分布无效（等级须为 S/A/B/C/D、合计须为 1.0 且不能为负数）', E9804248: '只有强制或混合模式可使用强制分布', E9804249: '强制分布仅支持 S/A/B/C/D 量表', E9804250: '当前周期阶段不允许此操作（必须处于校准阶段）', E9804251: '等级调整无效，请检查等级或目标评价', E9804931: '已确认或关闭的会议无法编辑、调整或再次确认', E9804932: '只有计划中的会议可以删除', E9804933: '只有校准阶段的评价可以调整', E9804449: '找不到报告', E9804252: '只有已定稿周期才能发布报告', E9804934: '已被替代的历史报告无法查看、确认或重新发布',
    E9804453: '找不到评价方案。', E9804454: '找不到评价对象。', E9804455: '找不到评价目标。', E9804456: '找不到评价提交内容。', E9804457: '找不到分数调整记录。', E9804458: '找不到评价反馈。', E9804302: '您无权使用此评价方案。', E9804256: '请检查评价方案设置。', E9804257: '当前评价阶段无法执行此操作。', E9804258: '目前不支持此评价类型。', E9804937: '进行中或已完成的评价无法重新设置。', E9804938: '继续前请完成所需的评价工作。', E9804939: '相同的评价方案或分配信息已存在。', E9804940: '评价结果尚未公开。', E9804460: '找不到请求的评价资料。', E9804360: '您无权使用此评价资料。', E9804260: '请检查评价资料的输入内容。', E9804960: '相同或冲突的评价资料已存在。', E9804961: '已完成或锁定的评价资料无法更改。', E9804261: '附件大小不得超过 20 MB。', E9804262: '不支持此附件格式。',
  },
  validation: { email: '请输入有效的电子邮箱地址。', password: '请输入密码。' },
  blockers: {
    MISSING_EMPLOYEE_BINDING: '请将账户关联到员工档案。', NO_ACTIVE_PARTICIPANTS: '请分配评价参与者。', PARTICIPANT_NOT_ACTIVE: '需要有效的评价参与者。', MISSING_MANAGER: '请分配主要评价人。', GOAL_MISSING: '请创建评价目标。', GOAL_NOT_APPROVED: '请检查评价政策和目标审批状态。', GOAL_ACTUAL_MISSING: '请记录目标的实际结果。', MID_REVIEW_INCOMPLETE: '请完成中期检查。', SELF_REVIEW_INCOMPLETE: '请提交自我评价。', MANAGER_REVIEW_INCOMPLETE: '请提交团队评价。', CALIBRATION_INCOMPLETE: '请确认等级校准。', REPORT_NOT_PUBLISHED: '请发布评价结果。', REPORT_NOT_ACKNOWLEDGED: '成员必须确认收到评价结果。', FEEDBACK_INCOMPLETE: '请完成反馈并解决申诉。',
  },
};
