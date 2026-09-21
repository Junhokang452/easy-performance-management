import { buildQueryKey } from '@easy/query-client';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { apiClient } from '../../../api/client';

const BASE = '/v1/evaluation-resources';

export type CatalogKind = 'PERFORMANCE' | 'COMPETENCY';
export type CatalogAssignmentType = 'JOB' | 'DEPARTMENT';
export type TaskStatus = 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'DISCARDED';
export type ProgressMode = 'CHECKLIST' | 'ACTUAL';
export type StakeholderRole = 'OWNER' | 'MANAGER' | 'COLLABORATOR';
export type InterviewView = 'AUTHORED' | 'MINE' | 'BY_EMPLOYEE' | 'REFERENCED';

export interface AchievementLevelInput {
  code: string;
  label: string;
  minValue: number | null;
  maxValue: number | null;
  description: string;
  displayOrder: number;
}

export interface CatalogAssignmentInput {
  type: CatalogAssignmentType;
  reference: string;
}

export interface CatalogUpsertRequest {
  kind: CatalogKind;
  category: string;
  name: string;
  definition: string;
  active: boolean;
  displayOrder: number;
  achievementLevels: AchievementLevelInput[];
  assignments: CatalogAssignmentInput[];
}

export interface AchievementLevelResponse extends AchievementLevelInput { id: string }
export interface CatalogAssignmentResponse extends CatalogAssignmentInput { id: string }

export interface CatalogResponse {
  id: string;
  kind: CatalogKind;
  category: string;
  name: string;
  definition: string;
  active: boolean;
  displayOrder: number;
  copiedFromId: string | null;
  achievementLevels: AchievementLevelResponse[];
  assignments: CatalogAssignmentResponse[];
  rowVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface DepartmentGoalUpsertRequest {
  year: number;
  periodStart: string;
  periodEnd: string;
  departmentId: string;
  catalogId: string | null;
  title: string;
  definition: string;
  weight: number;
  targetLevel: string;
  unit: string;
}

export interface DepartmentGoalResponse extends DepartmentGoalUpsertRequest {
  id: string;
  actualValue: number | null;
  achievementRate: number | null;
  actualNote: string | null;
  copiedFromId: string | null;
  transferredFromId: string | null;
  rowVersion: number;
  createdAt: string;
  updatedAt: string;
}

export interface StakeholderInput { employeeId: string; role: StakeholderRole }
export interface StakeholderResponse extends StakeholderInput {}

export interface TaskCreateRequest {
  title: string;
  periodStart: string;
  periodEnd: string;
  description: string;
  progressMode: ProgressMode;
  departmentGoalId: string | null;
  stakeholders: StakeholderInput[];
}

export type TaskUpdateRequest = Omit<TaskCreateRequest, 'progressMode'>;

export interface LabelResponse { id: string; name: string }
export interface LookupOptionResponse { value: string; label: string; description: string | null }
export interface EmployeeOptionResponse { id: string; employeeNo: string; name: string; departmentId: string | null }
export interface ChecklistResponse { id: string; text: string; completed: boolean; displayOrder: number; completedAt: string | null; completedBy: string | null }
export interface ActivityResponse { id: string; authorEmployeeId: string; type: string; message: string; progressPercent: number | null; fromStatus: TaskStatus | null; toStatus: TaskStatus | null; createdAt: string }
export interface TaskFeedbackResponse { id: string; fromEmployeeId: string; toEmployeeId: string; rating: number; message: string; createdAt: string }
export interface AttachmentResponse { id: string; filename: string; contentType: string; size: number; uploadedBy: string; createdAt: string }

export interface TaskSummaryResponse {
  id: string;
  title: string;
  periodStart: string;
  periodEnd: string;
  status: TaskStatus;
  progressMode: ProgressMode;
  progressPercent: number;
  departmentGoalId: string | null;
  stakeholders: StakeholderResponse[];
  labels: LabelResponse[];
  rowVersion: number;
  updatedAt: string;
}

export interface TaskDetailResponse {
  task: TaskSummaryResponse;
  description: string;
  checklist: ChecklistResponse[];
  activities: ActivityResponse[];
  feedback: TaskFeedbackResponse[];
  attachments: AttachmentResponse[];
}

export interface InterviewCreateRequest {
  subjectEmployeeId: string;
  occurredAt: string;
  summary: string;
  keyIssues: string;
  requests: string;
  followUp: string;
  subjectVisible: boolean;
  referencesVisible: boolean;
  referenceEmployeeIds: string[];
}

export interface InterviewAuditResponse {
  id: string;
  action: 'CREATED' | 'UPDATED' | 'VISIBILITY_CHANGED';
  actorEmployeeId: string;
  subjectVisible: boolean;
  referencesVisible: boolean;
  createdAt: string;
}

export interface InterviewResponse extends InterviewCreateRequest {
  id: string;
  authorEmployeeId: string;
  rowVersion: number;
  createdAt: string;
  updatedAt: string;
  auditTrail: InterviewAuditResponse[];
}

export const resourceKeys = {
  all: () => buildQueryKey('performance', 'evaluation-resources'),
  catalogs: (filters: object) => buildQueryKey('performance', 'evaluation-resources', 'catalogs', filters),
  catalog: (id: string) => buildQueryKey('performance', 'evaluation-resources', 'catalog', id),
  departmentGoals: (filters: object) => buildQueryKey('performance', 'evaluation-resources', 'department-goals', filters),
  departmentGoal: (id: string) => buildQueryKey('performance', 'evaluation-resources', 'department-goal', id),
  tasks: (filters: object) => buildQueryKey('performance', 'evaluation-resources', 'tasks', filters),
  task: (id: string) => buildQueryKey('performance', 'evaluation-resources', 'task', id),
  taskLabels: () => buildQueryKey('performance', 'evaluation-resources', 'task-labels'),
  interviews: (view: InterviewView, employeeId: string | null) => buildQueryKey('performance', 'evaluation-resources', 'interviews', { view, employeeId }),
  interview: (id: string) => buildQueryKey('performance', 'evaluation-resources', 'interview', id),
  lookup: (kind: string, query: string) => buildQueryKey('performance', 'evaluation-resources', 'lookup', kind, query),
  assignments: (employeeId: string) => buildQueryKey('performance', 'evaluation-resources', 'lookup', 'employees', employeeId, 'assignments'),
} as const;

const resourceApi = {
  listCatalogs: (params: { kind?: CatalogKind; includeInactive?: boolean }) => apiClient.get<CatalogResponse[]>(`${BASE}/catalogs`, { params }).then(({ data }) => data),
  getCatalog: (id: string) => apiClient.get<CatalogResponse>(`${BASE}/catalogs/${id}`).then(({ data }) => data),
  createCatalog: (input: CatalogUpsertRequest) => apiClient.post<CatalogResponse>(`${BASE}/catalogs`, input).then(({ data }) => data),
  updateCatalog: (id: string, input: CatalogUpsertRequest) => apiClient.put<CatalogResponse>(`${BASE}/catalogs/${id}`, input).then(({ data }) => data),
  copyCatalog: (id: string, name: string) => apiClient.post<CatalogResponse>(`${BASE}/catalogs/${id}/copy`, { name }).then(({ data }) => data),
  updateCatalogActive: (id: string, active: boolean) => apiClient.patch<CatalogResponse>(`${BASE}/catalogs/${id}/active`, { active }).then(({ data }) => data),
  deleteCatalog: (id: string) => apiClient.delete<CatalogResponse>(`${BASE}/catalogs/${id}`).then(({ data }) => data),
  listDepartmentGoals: (params: { departmentId?: string }) => apiClient.get<DepartmentGoalResponse[]>(`${BASE}/department-goals`, { params }).then(({ data }) => data),
  getDepartmentGoal: (id: string) => apiClient.get<DepartmentGoalResponse>(`${BASE}/department-goals/${id}`).then(({ data }) => data),
  createDepartmentGoal: (input: DepartmentGoalUpsertRequest) => apiClient.post<DepartmentGoalResponse>(`${BASE}/department-goals`, input).then(({ data }) => data),
  updateDepartmentGoal: (id: string, input: DepartmentGoalUpsertRequest) => apiClient.put<DepartmentGoalResponse>(`${BASE}/department-goals/${id}`, input).then(({ data }) => data),
  copyDepartmentGoal: (id: string, input: { year: number; periodStart: string; periodEnd: string; departmentId: string }) => apiClient.post<DepartmentGoalResponse>(`${BASE}/department-goals/${id}/copy`, input).then(({ data }) => data),
  transferDepartmentGoal: (id: string, targetDepartmentId: string, reason: string) => apiClient.post<DepartmentGoalResponse>(`${BASE}/department-goals/${id}/transfer`, { targetDepartmentId, reason }).then(({ data }) => data),
  recordDepartmentActual: (id: string, actualValue: number, achievementRate: number, note: string) => apiClient.post<DepartmentGoalResponse>(`${BASE}/department-goals/${id}/actual`, { actualValue, achievementRate, note }).then(({ data }) => data),
  listTasks: () => apiClient.get<TaskSummaryResponse[]>(`${BASE}/tasks`).then(({ data }) => data),
  getTask: (id: string) => apiClient.get<TaskDetailResponse>(`${BASE}/tasks/${id}`).then(({ data }) => data),
  createTask: (input: TaskCreateRequest) => apiClient.post<TaskDetailResponse>(`${BASE}/tasks`, input).then(({ data }) => data),
  updateTask: (id: string, input: TaskUpdateRequest) => apiClient.put<TaskDetailResponse>(`${BASE}/tasks/${id}`, input).then(({ data }) => data),
  changeTaskStatus: (id: string, status: TaskStatus, reason: string) => apiClient.post<TaskDetailResponse>(`${BASE}/tasks/${id}/status`, { status, reason }).then(({ data }) => data),
  reopenTask: (id: string, reason: string) => apiClient.post<TaskDetailResponse>(`${BASE}/tasks/${id}/reopen`, { reason }).then(({ data }) => data),
  addChecklist: (id: string, text: string, displayOrder: number) => apiClient.post<ChecklistResponse>(`${BASE}/tasks/${id}/checklist`, { text, displayOrder }).then(({ data }) => data),
  updateChecklist: (id: string, itemId: string, completed: boolean) => apiClient.patch<ChecklistResponse>(`${BASE}/tasks/${id}/checklist/${itemId}`, { completed }).then(({ data }) => data),
  recordTaskProgress: (id: string, progressPercent: number, note: string) => apiClient.post<TaskDetailResponse>(`${BASE}/tasks/${id}/progress`, { progressPercent, note }).then(({ data }) => data),
  addTaskActivity: (id: string, message: string) => apiClient.post<ActivityResponse>(`${BASE}/tasks/${id}/activities`, { message }).then(({ data }) => data),
  addTaskFeedback: (id: string, toEmployeeId: string, rating: number, message: string) => apiClient.post<TaskFeedbackResponse>(`${BASE}/tasks/${id}/feedback`, { toEmployeeId, rating, message }).then(({ data }) => data),
  listTaskLabels: () => apiClient.get<LabelResponse[]>(`${BASE}/task-labels`).then(({ data }) => data),
  createTaskLabel: (name: string) => apiClient.post<LabelResponse>(`${BASE}/task-labels`, { name }).then(({ data }) => data),
  applyTaskLabel: (id: string, labelId: string) => apiClient.post<TaskDetailResponse>(`${BASE}/tasks/${id}/labels`, { labelId }).then(({ data }) => data),
  removeTaskLabel: (id: string, labelId: string) => apiClient.delete<TaskDetailResponse>(`${BASE}/tasks/${id}/labels/${labelId}`).then(({ data }) => data),
  uploadTaskAttachment: (id: string, file: File) => {
    const body = new FormData();
    body.append('file', file);
    return apiClient.post<AttachmentResponse>(`${BASE}/tasks/${id}/attachments`, body).then(({ data }) => data);
  },
  downloadTaskAttachment: (id: string, attachmentId: string) => apiClient.get<Blob>(`${BASE}/tasks/${id}/attachments/${attachmentId}`, { responseType: 'blob', headers: { Accept: '*/*' } }).then(({ data }) => data),
  listInterviews: (view: InterviewView, employeeId?: string) => apiClient.get<InterviewResponse[]>(`${BASE}/interviews`, { params: { view, employeeId } }).then(({ data }) => data),
  getInterview: (id: string) => apiClient.get<InterviewResponse>(`${BASE}/interviews/${id}`).then(({ data }) => data),
  createInterview: (input: InterviewCreateRequest) => apiClient.post<InterviewResponse>(`${BASE}/interviews`, input).then(({ data }) => data),
  updateInterview: (id: string, input: Pick<InterviewCreateRequest, 'occurredAt' | 'summary' | 'keyIssues' | 'requests' | 'followUp'> & { referenceEmployeeIds?: string[] }) => apiClient.put<InterviewResponse>(`${BASE}/interviews/${id}`, input).then(({ data }) => data),
  updateInterviewVisibility: (id: string, subjectVisible: boolean, referencesVisible: boolean) => apiClient.patch<InterviewResponse>(`${BASE}/interviews/${id}/visibility`, { subjectVisible, referencesVisible }).then(({ data }) => data),
  lookupEmployees: (q: string) => apiClient.get<EmployeeOptionResponse[]>(`${BASE}/lookup/employees`, { params: { q } }).then(({ data }) => data),
  lookupAssignments: (employeeId: string) => apiClient.get<LookupOptionResponse[]>(`${BASE}/lookup/employees/${employeeId}/assignments`).then(({ data }) => data),
  lookupDepartments: (q: string) => apiClient.get<LookupOptionResponse[]>(`${BASE}/lookup/departments`, { params: { q } }).then(({ data }) => data),
  lookupJobs: (q: string) => apiClient.get<LookupOptionResponse[]>(`${BASE}/lookup/jobs`, { params: { q } }).then(({ data }) => data),
};

export function useCatalogsQuery(filters: { kind?: CatalogKind; includeInactive?: boolean }) {
  return useQuery({ queryKey: resourceKeys.catalogs(filters), queryFn: () => resourceApi.listCatalogs(filters) });
}
export function useCatalogQuery(id: string | null) { return useQuery({ queryKey: resourceKeys.catalog(id ?? ''), queryFn: () => resourceApi.getCatalog(id as string), enabled: Boolean(id) }); }
export function useDepartmentGoalsQuery(filters: { departmentId?: string }) { return useQuery({ queryKey: resourceKeys.departmentGoals(filters), queryFn: () => resourceApi.listDepartmentGoals(filters) }); }
export function useDepartmentGoalQuery(id: string | null) { return useQuery({ queryKey: resourceKeys.departmentGoal(id ?? ''), queryFn: () => resourceApi.getDepartmentGoal(id as string), enabled: Boolean(id) }); }
export function useTasksQuery(filters: { status?: TaskStatus; q?: string; labelId?: string }) { return useQuery({ queryKey: resourceKeys.tasks(filters), queryFn: resourceApi.listTasks, select: (items) => items.filter((item) => (!filters.status || item.status === filters.status) && (!filters.q || item.title.toLocaleLowerCase().includes(filters.q.toLocaleLowerCase())) && (!filters.labelId || item.labels.some((label) => label.id === filters.labelId))) }); }
export function useTaskQuery(id: string | null) { return useQuery({ queryKey: resourceKeys.task(id ?? ''), queryFn: () => resourceApi.getTask(id as string), enabled: Boolean(id) }); }
export function useTaskLabelsQuery() { return useQuery({ queryKey: resourceKeys.taskLabels(), queryFn: resourceApi.listTaskLabels }); }
export function useInterviewsQuery(view: InterviewView, employeeId: string | null) { return useQuery({ queryKey: resourceKeys.interviews(view, employeeId), queryFn: () => resourceApi.listInterviews(view, employeeId ?? undefined), enabled: view !== 'BY_EMPLOYEE' || Boolean(employeeId) }); }
export function useInterviewQuery(id: string | null) { return useQuery({ queryKey: resourceKeys.interview(id ?? ''), queryFn: () => resourceApi.getInterview(id as string), enabled: Boolean(id) }); }
export function useEmployeeOptionsQuery(q: string) { return useQuery({ queryKey: resourceKeys.lookup('employees', q), queryFn: () => resourceApi.lookupEmployees(q) }); }
export function useEmployeeAssignmentsQuery(employeeId: string | null) { return useQuery({ queryKey: resourceKeys.assignments(employeeId ?? ''), queryFn: () => resourceApi.lookupAssignments(employeeId as string), enabled: Boolean(employeeId) }); }
export function useDepartmentOptionsQuery(q: string) { return useQuery({ queryKey: resourceKeys.lookup('departments', q), queryFn: () => resourceApi.lookupDepartments(q) }); }
export function useJobOptionsQuery(q: string) { return useQuery({ queryKey: resourceKeys.lookup('jobs', q), queryFn: () => resourceApi.lookupJobs(q) }); }

function useResourceMutation<TInput, TResult>(mutationFn: (input: TInput) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({ mutationFn, onSuccess: () => queryClient.invalidateQueries({ queryKey: resourceKeys.all() }) });
}

export const useCreateCatalogMutation = () => useResourceMutation(resourceApi.createCatalog);
export const useUpdateCatalogMutation = () => useResourceMutation(({ id, input }: { id: string; input: CatalogUpsertRequest }) => resourceApi.updateCatalog(id, input));
export const useCopyCatalogMutation = () => useResourceMutation(({ id, name }: { id: string; name: string }) => resourceApi.copyCatalog(id, name));
export const useCatalogActiveMutation = () => useResourceMutation(({ id, active }: { id: string; active: boolean }) => resourceApi.updateCatalogActive(id, active));
export const useDeleteCatalogMutation = () => useResourceMutation((id: string) => resourceApi.deleteCatalog(id));
export const useCreateDepartmentGoalMutation = () => useResourceMutation(resourceApi.createDepartmentGoal);
export const useUpdateDepartmentGoalMutation = () => useResourceMutation(({ id, input }: { id: string; input: DepartmentGoalUpsertRequest }) => resourceApi.updateDepartmentGoal(id, input));
export const useCopyDepartmentGoalMutation = () => useResourceMutation(({ id, input }: { id: string; input: Parameters<typeof resourceApi.copyDepartmentGoal>[1] }) => resourceApi.copyDepartmentGoal(id, input));
export const useTransferDepartmentGoalMutation = () => useResourceMutation(({ id, targetDepartmentId, reason }: { id: string; targetDepartmentId: string; reason: string }) => resourceApi.transferDepartmentGoal(id, targetDepartmentId, reason));
export const useRecordDepartmentActualMutation = () => useResourceMutation(({ id, actualValue, achievementRate, note }: { id: string; actualValue: number; achievementRate: number; note: string }) => resourceApi.recordDepartmentActual(id, actualValue, achievementRate, note));
export const useCreateTaskMutation = () => useResourceMutation(resourceApi.createTask);
export const useUpdateTaskMutation = () => useResourceMutation(({ id, input }: { id: string; input: TaskUpdateRequest }) => resourceApi.updateTask(id, input));
export const useChangeTaskStatusMutation = () => useResourceMutation(({ id, status, reason }: { id: string; status: TaskStatus; reason: string }) => resourceApi.changeTaskStatus(id, status, reason));
export const useReopenTaskMutation = () => useResourceMutation(({ id, reason }: { id: string; reason: string }) => resourceApi.reopenTask(id, reason));
export const useAddChecklistMutation = () => useResourceMutation(({ id, text, displayOrder }: { id: string; text: string; displayOrder: number }) => resourceApi.addChecklist(id, text, displayOrder));
export const useUpdateChecklistMutation = () => useResourceMutation(({ id, itemId, completed }: { id: string; itemId: string; completed: boolean }) => resourceApi.updateChecklist(id, itemId, completed));
export const useRecordTaskProgressMutation = () => useResourceMutation(({ id, progressPercent, note }: { id: string; progressPercent: number; note: string }) => resourceApi.recordTaskProgress(id, progressPercent, note));
export const useAddTaskActivityMutation = () => useResourceMutation(({ id, message }: { id: string; message: string }) => resourceApi.addTaskActivity(id, message));
export const useAddTaskFeedbackMutation = () => useResourceMutation(({ id, toEmployeeId, rating, message }: { id: string; toEmployeeId: string; rating: number; message: string }) => resourceApi.addTaskFeedback(id, toEmployeeId, rating, message));
export const useCreateTaskLabelMutation = () => useResourceMutation(resourceApi.createTaskLabel);
export const useApplyTaskLabelMutation = () => useResourceMutation(({ id, labelId }: { id: string; labelId: string }) => resourceApi.applyTaskLabel(id, labelId));
export const useRemoveTaskLabelMutation = () => useResourceMutation(({ id, labelId }: { id: string; labelId: string }) => resourceApi.removeTaskLabel(id, labelId));
export const useUploadTaskAttachmentMutation = () => useResourceMutation(({ id, file }: { id: string; file: File }) => resourceApi.uploadTaskAttachment(id, file));
export const useCreateInterviewMutation = () => useResourceMutation(resourceApi.createInterview);
export const useUpdateInterviewMutation = () => useResourceMutation(({ id, input }: { id: string; input: Pick<InterviewCreateRequest, 'occurredAt' | 'summary' | 'keyIssues' | 'requests' | 'followUp'> & { referenceEmployeeIds?: string[] } }) => resourceApi.updateInterview(id, input));
export const useUpdateInterviewVisibilityMutation = () => useResourceMutation(({ id, subjectVisible, referencesVisible }: { id: string; subjectVisible: boolean; referencesVisible: boolean }) => resourceApi.updateInterviewVisibility(id, subjectVisible, referencesVisible));

export { resourceApi };
