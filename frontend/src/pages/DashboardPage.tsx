import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useCurrentUser } from '../hooks/useCurrentUser';
import { useRepos, useSyncRepos } from '../hooks/useRepos';
import { api } from '../lib/api';
import type { Repo } from '../lib/api';

const LANGUAGE_COLORS: Record<string, string> = {
  TypeScript: 'bg-blue-500',
  JavaScript: 'bg-yellow-400',
  Python: 'bg-green-500',
  Java: 'bg-orange-500',
  Go: 'bg-cyan-400',
  Rust: 'bg-orange-700',
  'C++': 'bg-pink-500',
  Ruby: 'bg-red-500',
  Swift: 'bg-orange-400',
  Kotlin: 'bg-purple-500',
};

function RepoCard({ repo, selected, onToggle }: { repo: Repo; selected: boolean; onToggle: () => void }) {
  return (
    <div
      onClick={onToggle}
      className={`relative p-4 rounded-xl border cursor-pointer transition-all ${
        selected
          ? 'border-violet-500 bg-violet-500/10'
          : 'border-gray-800 bg-gray-900 hover:border-gray-600'
      }`}
    >
      {selected && (
        <div className="absolute top-3 right-3 w-5 h-5 rounded-full bg-violet-500 flex items-center justify-center">
          <svg className="w-3 h-3 text-white" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={3}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
          </svg>
        </div>
      )}

      <div className="pr-6">
        <h3 className="font-semibold text-white text-sm mb-1 truncate">{repo.name}</h3>
        {repo.description && (
          <p className="text-gray-400 text-xs mb-3 line-clamp-2">{repo.description}</p>
        )}
      </div>

      <div className="flex items-center gap-3 text-xs text-gray-500">
        {repo.primaryLanguage && (
          <span className="flex items-center gap-1">
            <span className={`w-2 h-2 rounded-full ${LANGUAGE_COLORS[repo.primaryLanguage] ?? 'bg-gray-400'}`} />
            {repo.primaryLanguage}
          </span>
        )}
        <span className="flex items-center gap-1">
          <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
          </svg>
          {repo.stars}
        </span>
        <span className="flex items-center gap-1">
          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M8.684 13.342C8.886 12.938 9 12.482 9 12c0-.482-.114-.938-.316-1.342m0 2.684a3 3 0 110-2.684m0 2.684l6.632 3.316m-6.632-6l6.632-3.316m0 0a3 3 0 105.367-2.684 3 3 0 00-5.367 2.684zm0 9.316a3 3 0 105.368 2.684 3 3 0 00-5.368-2.684z" />
          </svg>
          {repo.forks}
        </span>
      </div>

      {repo.topics.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-2">
          {repo.topics.slice(0, 3).map(t => (
            <span key={t} className="px-1.5 py-0.5 bg-gray-800 text-gray-400 text-xs rounded">
              {t}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

export default function DashboardPage() {
  const { data: user } = useCurrentUser();
  const { data: repos = [], isLoading: reposLoading } = useRepos();
  const syncMutation = useSyncRepos();

  const navigate = useNavigate();
  const [search, setSearch] = useState('');
  const [langFilter, setLangFilter] = useState('');
  const [selected, setSelected] = useState<Set<string>>(new Set());

  const analyzeMutation = useMutation({
    mutationFn: (ids: string[]) => api.analysis.batch(ids),
    onSuccess: () => navigate('/status'),
  });

  const languages = [...new Set(repos.map(r => r.primaryLanguage).filter(Boolean))].sort();

  const filtered = repos.filter(r => {
    const matchesSearch = r.name.toLowerCase().includes(search.toLowerCase()) ||
      r.description.toLowerCase().includes(search.toLowerCase());
    const matchesLang = !langFilter || r.primaryLanguage === langFilter;
    return matchesSearch && matchesLang;
  });

  const toggleSelect = (id: string) => {
    setSelected(prev => {
      const next = new Set(prev);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  };

  const handleLogout = () => { window.location.href = '/auth/logout'; };

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      {/* Header */}
      <header className="border-b border-gray-800 px-6 py-4 flex items-center justify-between">
        <h1 className="text-lg font-semibold">GitHub Portfolio Intelligence</h1>
        <div className="flex items-center gap-4">
          {user?.avatarUrl && (
            <img src={user.avatarUrl} alt={user.username} className="w-8 h-8 rounded-full" />
          )}
          <span className="text-gray-300 text-sm">{user?.username}</span>
          <button
            onClick={() => navigate('/workspace')}
            className="text-sm text-gray-400 hover:text-white transition-colors"
          >
            Workspace
          </button>
          <button onClick={handleLogout} className="text-sm text-gray-400 hover:text-white transition-colors">
            Log out
          </button>
        </div>
      </header>

      <main className="max-w-6xl mx-auto px-6 py-8">
        {/* Top bar */}
        <div className="flex items-center justify-between mb-6">
          <div>
            <h2 className="text-2xl font-bold">Your Repositories</h2>
            <p className="text-gray-400 text-sm mt-1">
              {repos.length > 0 ? `${repos.length} repos synced` : 'Sync to load your repositories'}
            </p>
          </div>
          <button
            onClick={() => syncMutation.mutate()}
            disabled={syncMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
          >
            <svg className={`w-4 h-4 ${syncMutation.isPending ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {syncMutation.isPending ? 'Syncing...' : 'Sync Repos'}
          </button>
        </div>

        {/* Filters */}
        {repos.length > 0 && (
          <div className="flex gap-3 mb-6">
            <input
              type="text"
              placeholder="Search repositories..."
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="flex-1 px-4 py-2 bg-gray-900 border border-gray-700 rounded-lg text-sm text-white placeholder-gray-500 focus:outline-none focus:border-violet-500"
            />
            <select
              value={langFilter}
              onChange={e => setLangFilter(e.target.value)}
              className="px-4 py-2 bg-gray-900 border border-gray-700 rounded-lg text-sm text-white focus:outline-none focus:border-violet-500"
            >
              <option value="">All languages</option>
              {languages.map(l => <option key={l} value={l}>{l}</option>)}
            </select>
            {selected.size > 0 && (
              <button
                onClick={() => analyzeMutation.mutate([...selected])}
                disabled={analyzeMutation.isPending}
                className="px-4 py-2 bg-violet-600 hover:bg-violet-500 rounded-lg text-sm font-semibold transition-colors disabled:opacity-50"
              >
                {analyzeMutation.isPending ? 'Analyzing...' : `Analyze ${selected.size} repo${selected.size > 1 ? 's' : ''}`}
              </button>
            )}
          </div>
        )}

        {/* Content */}
        {reposLoading ? (
          <div className="text-center py-20 text-gray-500">Loading...</div>
        ) : repos.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-gray-400 mb-4">No repositories synced yet.</p>
            <button
              onClick={() => syncMutation.mutate()}
              disabled={syncMutation.isPending}
              className="px-6 py-3 bg-violet-600 hover:bg-violet-500 rounded-xl font-semibold transition-colors disabled:opacity-50"
            >
              {syncMutation.isPending ? 'Syncing...' : 'Sync Now'}
            </button>
          </div>
        ) : (
          <>
            {selected.size > 0 && (
              <p className="text-sm text-violet-400 mb-4">{selected.size} selected — click Analyze to generate portfolio content</p>
            )}
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
              {filtered.map(repo => (
                <RepoCard
                  key={repo.id}
                  repo={repo}
                  selected={selected.has(repo.id)}
                  onToggle={() => toggleSelect(repo.id)}
                />
              ))}
            </div>
            {filtered.length === 0 && (
              <div className="text-center py-20 text-gray-500">No repos match your filters.</div>
            )}
          </>
        )}
      </main>
    </div>
  );
}
