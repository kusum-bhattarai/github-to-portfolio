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

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, { credentials: 'include', ...options });
  if (!res.ok) {
    const err = new Error(`HTTP ${res.status}`);
    (err as any).status = res.status;
    throw err;
  }
  return res.json();
}

export interface ContentBlock {
  id: string;
  contentType: 'PORTFOLIO_SUMMARY' | 'RESUME_BULLETS' | 'TECH_STACK' | 'PROJECT_TAGS';
  generatedText: string;
  createdAt: string;
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
};
