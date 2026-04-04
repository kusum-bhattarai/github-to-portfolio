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

export const api = {
  me: () => request<CurrentUser>('/api/me'),
  repos: {
    list: () => request<Repo[]>('/api/repos'),
    sync: () => request<Repo[]>('/api/repos/sync', { method: 'POST' }),
  },
};
