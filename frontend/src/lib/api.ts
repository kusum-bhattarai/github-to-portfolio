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

export interface ContentBlock {
  id: string;
  contentType: 'PORTFOLIO_SUMMARY' | 'RESUME_BULLETS' | 'TECH_STACK' | 'PROJECT_TAGS';
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

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, { credentials: 'include', ...options });
  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`);
    (err as any).status = res.status;
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
    analyze: (repoId: string) => request<ContentBlock[]>(`/api/repos/${repoId}/analyze`, { method: 'POST' }),
    getContent: (repoId: string) => request<ContentBlock[]>(`/api/projects/${repoId}/content`),
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
