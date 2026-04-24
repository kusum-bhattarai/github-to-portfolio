export interface CurrentUser {
  id: string;
  username: string;
  email: string;
  avatarUrl: string;
  createdAt: string;
}

export interface Repo {
  id: string;
  githubRepoId: number;
  name: string;
  fullName: string;
  description: string;
  primaryLanguage: string;
  stars: number;
  forks: number;
  topics: string[];
  htmlUrl: string;
}

export type ContentType =
  | 'PORTFOLIO_SUMMARY'
  | 'RESUME_BULLETS'
  | 'TECH_STACK'
  | 'PROJECT_TAGS'
  | 'INTERVIEW_STORY'
  | 'ONE_SENTENCE_PITCH'
  | 'TALKING_POINTS';

export interface ContentBlock {
  id: string;
  contentType: ContentType;
  generatedText: string;
  editedText: string | null;
  isEdited: boolean;
  createdAt: string;
}

export interface ProjectSummary {
  repoId: string;
  repoName: string;
  repoFullName: string;
  description: string | null;
  primaryLanguage: string | null;
  stars: number;
  htmlUrl: string;
  analyzedAt: string | null;
  projectType: string | null;
  projectTags: string | null;
  portfolioSummary: string | null;
}

export type JobStatus = 'QUEUED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'RETRYING';

export interface AnalysisJob {
  jobId: string;
  repoId: string;
  repoName: string;
  repoFullName?: string;
  status: JobStatus;
  isActive: boolean;
  createdAt: string;
  startedAt: string | null;
  completedAt: string | null;
  errorMessage: string | null;
}

export const BACKEND_URL = import.meta.env.VITE_BACKEND_URL ?? '';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(`${BACKEND_URL}${path}`, { credentials: 'include', ...options });
  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`);
    (err as Error & { status: number }).status = res.status;
    throw err;
  }
  return res.json();
}

export const api = {
  me: () => request<CurrentUser>('/api/me'),
  repos: {
    list: () => request<Repo[]>('/api/repos'),
    sync: () => request<Repo[]>('/api/repos/sync', { method: 'POST' }),
  },
  analysis: {
    // Single repo — used for reanalyze from ResultsPage
    analyze: (repoId: string) => request<AnalysisJob>(`/api/repos/${repoId}/analyze`, { method: 'POST' }),
    // Batch — used from Dashboard for multiple repos
    batch: (repoIds: string[]) =>
      request<AnalysisJob[]>('/api/repos/analyze/batch', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ repoIds }),
      }),
    getContent: (repoId: string) => request<ContentBlock[]>(`/api/projects/${repoId}/content`),
  },
  jobs: {
    get: (jobId: string) => request<AnalysisJob>(`/api/jobs/${jobId}`),
    list: (page = 0, size = 20) =>
      request<{ content: AnalysisJob[]; totalElements: number; totalPages: number; last: boolean }>(
        `/api/jobs?page=${page}&size=${size}`
      ).then(r => r.content),
  },
  projects: {
    list: () => request<ProjectSummary[]>('/api/projects'),
    saveEdit: (repoId: string, contentId: string, text: string) =>
      request(`/api/projects/${repoId}/content/${contentId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ text }),
      }),
  },
};
