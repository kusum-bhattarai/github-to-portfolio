import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import { useRepos, useSyncRepos } from '../hooks/useRepos';
import { api } from '../lib/api';
import type { Repo } from '../lib/api';
import Layout from '../components/Layout';

const LANG_COLORS: Record<string, string> = {
  TypeScript:  '#3b82f6',
  JavaScript:  '#eab308',
  Python:      '#22c55e',
  Java:        '#f97316',
  Go:          '#06b6d4',
  Rust:        '#c2410c',
  'C++':       '#ec4899',
  Ruby:        '#ef4444',
  Swift:       '#fb923c',
  Kotlin:      '#a855f7',
  'C#':        '#8b5cf6',
  PHP:         '#6366f1',
};

function RepoCard({ repo, selected, onToggle }: { repo: Repo; selected: boolean; onToggle: () => void }) {
  return (
    <div
      onClick={onToggle}
      className="relative p-4 rounded-xl cursor-pointer transition-all duration-150 select-none"
      style={{
        background: selected ? 'rgba(240,160,48,0.07)' : 'var(--bg-card)',
        border: `1px solid ${selected ? 'var(--accent-border)' : 'var(--border)'}`,
        boxShadow: selected ? '0 0 0 1px var(--accent-border)' : 'none',
      }}
      onMouseEnter={e => {
        if (!selected) {
          (e.currentTarget as HTMLElement).style.borderColor = 'var(--border-light)';
          (e.currentTarget as HTMLElement).style.background = 'var(--bg-card-hover)';
        }
      }}
      onMouseLeave={e => {
        if (!selected) {
          (e.currentTarget as HTMLElement).style.borderColor = 'var(--border)';
          (e.currentTarget as HTMLElement).style.background = 'var(--bg-card)';
        }
      }}
    >
      {/* Selection indicator */}
      <div
        className="absolute top-3 right-3 w-4 h-4 rounded-full flex items-center justify-center transition-all duration-150"
        style={{
          background: selected ? 'var(--accent)' : 'transparent',
          border: `1.5px solid ${selected ? 'var(--accent)' : 'var(--border-light)'}`,
        }}
      >
        {selected && (
          <svg className="w-2.5 h-2.5 text-black" viewBox="0 0 12 12" fill="none">
            <path d="M2 6l3 3 5-5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" />
          </svg>
        )}
      </div>

      <div className="pr-6">
        <h3
          className="font-medium text-sm mb-1 truncate"
          style={{ color: 'var(--text-primary)' }}
        >
          {repo.name}
        </h3>
        {repo.description && (
          <p
            className="text-xs mb-3 line-clamp-2 leading-relaxed"
            style={{ color: 'var(--text-secondary)' }}
          >
            {repo.description}
          </p>
        )}
      </div>

      <div className="flex items-center gap-3 text-xs" style={{ color: 'var(--text-muted)' }}>
        {repo.primaryLanguage && (
          <span className="flex items-center gap-1.5">
            <span
              className="w-2 h-2 rounded-full shrink-0"
              style={{ background: LANG_COLORS[repo.primaryLanguage] ?? '#6b7280' }}
            />
            {repo.primaryLanguage}
          </span>
        )}
        <span className="flex items-center gap-1">
          <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 24 24">
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
          </svg>
          {repo.stars}
        </span>
        <span>{repo.forks} forks</span>
      </div>

      {repo.topics.length > 0 && (
        <div className="flex flex-wrap gap-1 mt-2.5">
          {repo.topics.slice(0, 3).map(t => (
            <span
              key={t}
              className="px-1.5 py-0.5 text-xs rounded font-mono-dm"
              style={{ background: 'var(--bg-surface)', color: 'var(--text-muted)', border: '1px solid var(--border)' }}
            >
              {t}
            </span>
          ))}
        </div>
      )}
    </div>
  );
}

function EmptyState({ onSync, isPending }: { onSync: () => void; isPending: boolean }) {
  return (
    <div className="flex flex-col items-center justify-center py-28 animate-fade-up">
      <div
        className="w-14 h-14 rounded-2xl flex items-center justify-center mb-5"
        style={{ background: 'var(--bg-card)', border: '1px solid var(--border)' }}
      >
        <svg className="w-7 h-7" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5} style={{ color: 'var(--text-muted)' }}>
          <path strokeLinecap="round" strokeLinejoin="round" d="M3 7h18M3 12h18M3 17h18" />
        </svg>
      </div>
      <p className="font-medium mb-1.5" style={{ color: 'var(--text-primary)' }}>No repositories synced yet</p>
      <p className="text-sm mb-6" style={{ color: 'var(--text-muted)' }}>Pull your GitHub repos to get started</p>
      <button
        onClick={onSync}
        disabled={isPending}
        className="px-5 py-2.5 rounded-xl text-sm font-semibold transition-all duration-200 disabled:opacity-50"
        style={{ background: 'var(--accent)', color: '#0a0906' }}
        onMouseEnter={e => { (e.currentTarget as HTMLElement).style.filter = 'brightness(1.1)'; }}
        onMouseLeave={e => { (e.currentTarget as HTMLElement).style.filter = 'none'; }}
      >
        {isPending ? 'Syncing…' : 'Sync GitHub Repos'}
      </button>
    </div>
  );
}

export default function DashboardPage() {
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

  const languages = [...new Set(repos.map(r => r.primaryLanguage).filter(Boolean))].sort() as string[];

  const filtered = repos.filter(r => {
    const q = search.toLowerCase();
    const matchesSearch = r.name.toLowerCase().includes(q) || r.description?.toLowerCase().includes(q);
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

  return (
    <Layout
      maxWidth="2xl"
      navLinks={[
        { label: 'Queue', to: '/status' },
        { label: 'Workspace', to: '/workspace' },
      ]}
    >
      {/* Page header */}
      <div className="flex items-start justify-between mb-7 animate-fade-up">
        <div>
          <h1 className="font-display text-3xl mb-1" style={{ color: 'var(--text-primary)' }}>
            Your Repositories
          </h1>
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
            {repos.length > 0
              ? `${repos.length} repos synced · select any to analyze`
              : 'Sync your GitHub account to load repositories'}
          </p>
        </div>
        <button
          onClick={() => syncMutation.mutate()}
          disabled={syncMutation.isPending}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all duration-150 disabled:opacity-50 shrink-0"
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border)',
            color: 'var(--text-secondary)',
          }}
          onMouseEnter={e => {
            (e.currentTarget as HTMLElement).style.borderColor = 'var(--border-light)';
            (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)';
          }}
          onMouseLeave={e => {
            (e.currentTarget as HTMLElement).style.borderColor = 'var(--border)';
            (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)';
          }}
        >
          <svg
            className={`w-4 h-4 ${syncMutation.isPending ? 'animate-spin' : ''}`}
            fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          {syncMutation.isPending ? 'Syncing…' : 'Sync repos'}
        </button>
      </div>

      {/* Filters */}
      {repos.length > 0 && (
        <div className="flex gap-2.5 mb-6 animate-fade-up stagger-1">
          <div className="relative flex-1">
            <svg
              className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5"
              fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
              style={{ color: 'var(--text-muted)' }}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              placeholder="Search repositories…"
              value={search}
              onChange={e => setSearch(e.target.value)}
              className="w-full pl-9 pr-4 py-2 rounded-lg text-sm outline-none transition-all"
              style={{
                background: 'var(--bg-card)',
                border: '1px solid var(--border)',
                color: 'var(--text-primary)',
              }}
              onFocus={e => { (e.currentTarget as HTMLElement).style.borderColor = 'var(--accent-border)'; }}
              onBlur={e => { (e.currentTarget as HTMLElement).style.borderColor = 'var(--border)'; }}
            />
          </div>

          <select
            value={langFilter}
            onChange={e => setLangFilter(e.target.value)}
            className="px-3 py-2 rounded-lg text-sm outline-none transition-all"
            style={{
              background: 'var(--bg-card)',
              border: '1px solid var(--border)',
              color: langFilter ? 'var(--text-primary)' : 'var(--text-muted)',
            }}
          >
            <option value="">All languages</option>
            {languages.map(l => <option key={l} value={l}>{l}</option>)}
          </select>

          {selected.size > 0 && (
            <button
              onClick={() => analyzeMutation.mutate([...selected])}
              disabled={analyzeMutation.isPending}
              className="px-4 py-2 rounded-lg text-sm font-semibold transition-all duration-150 disabled:opacity-50 shrink-0"
              style={{ background: 'var(--accent)', color: '#0a0906' }}
              onMouseEnter={e => { (e.currentTarget as HTMLElement).style.filter = 'brightness(1.1)'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLElement).style.filter = 'none'; }}
            >
              {analyzeMutation.isPending
                ? 'Analyzing…'
                : `Analyze ${selected.size} repo${selected.size !== 1 ? 's' : ''}`}
            </button>
          )}
        </div>
      )}

      {/* Selection hint */}
      {selected.size > 0 && (
        <p
          className="text-xs mb-4 animate-fade-in font-mono-dm"
          style={{ color: 'var(--accent-dim)' }}
        >
          {selected.size} selected — click Analyze to generate portfolio content
        </p>
      )}

      {/* Content */}
      {reposLoading ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {[...Array(9)].map((_, i) => (
            <div
              key={i}
              className="rounded-xl p-4 h-28 skeleton"
              style={{ border: '1px solid var(--border)' }}
            />
          ))}
        </div>
      ) : repos.length === 0 ? (
        <EmptyState onSync={() => syncMutation.mutate()} isPending={syncMutation.isPending} />
      ) : filtered.length === 0 ? (
        <div className="text-center py-20" style={{ color: 'var(--text-muted)' }}>
          No repositories match your filters.
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 animate-fade-up stagger-2">
          {filtered.map(repo => (
            <RepoCard
              key={repo.id}
              repo={repo}
              selected={selected.has(repo.id)}
              onToggle={() => toggleSelect(repo.id)}
            />
          ))}
        </div>
      )}
    </Layout>
  );
}
