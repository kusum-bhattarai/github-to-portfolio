import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { ProjectSummary } from '../lib/api';
import Layout from '../components/Layout';

function timeAgo(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const mins = Math.floor(diff / 60_000);
  if (mins < 60) return `${mins || 1}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(iso).toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

const LANG_COLORS: Record<string, string> = {
  TypeScript:  '#3b82f6',
  JavaScript:  '#eab308',
  Python:      '#22c55e',
  Java:        '#f97316',
  Go:          '#06b6d4',
  Rust:        '#c2410c',
  Ruby:        '#ef4444',
  'C++':       '#ec4899',
  'C#':        '#8b5cf6',
  Kotlin:      '#a855f7',
  Swift:       '#fb923c',
};

function SkeletonCard() {
  return (
    <div className="rounded-xl p-5 skeleton" style={{ border: '1px solid var(--border)', height: '200px' }} />
  );
}

function ProjectCard({ project }: { project: ProjectSummary }) {
  const navigate = useNavigate();
  const tags = project.projectTags
    ? project.projectTags.split(',').map(t => t.trim()).filter(Boolean)
    : [];
  const langColor = project.primaryLanguage ? (LANG_COLORS[project.primaryLanguage] ?? '#6b7280') : null;

  return (
    <div
      className="rounded-xl p-5 flex flex-col gap-4 transition-all duration-150 group"
      style={{ background: 'var(--bg-card)', border: '1px solid var(--border)' }}
      onMouseEnter={e => {
        (e.currentTarget as HTMLElement).style.borderColor = 'var(--border-light)';
        (e.currentTarget as HTMLElement).style.background = 'var(--bg-card-hover)';
      }}
      onMouseLeave={e => {
        (e.currentTarget as HTMLElement).style.borderColor = 'var(--border)';
        (e.currentTarget as HTMLElement).style.background = 'var(--bg-card)';
      }}
    >
      {/* Top */}
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <h3 className="text-sm font-semibold truncate" style={{ color: 'var(--text-primary)' }}>
            {project.repoName}
          </h3>
          {langColor && (
            <span className="flex items-center gap-1.5 mt-1 text-xs" style={{ color: 'var(--text-secondary)' }}>
              <span className="w-2 h-2 rounded-full shrink-0" style={{ background: langColor }} />
              {project.primaryLanguage}
            </span>
          )}
        </div>
        <span className="flex items-center gap-1 text-xs shrink-0" style={{ color: 'var(--text-muted)' }}>
          <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20" style={{ color: '#f0a030' }}>
            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
          </svg>
          {project.stars.toLocaleString()}
        </span>
      </div>

      {/* Type badge */}
      {project.projectType && (
        <span
          className="self-start px-2 py-0.5 rounded-full text-[11px] font-medium font-mono-dm"
          style={{ background: 'var(--accent-glow)', border: '1px solid var(--accent-border)', color: 'var(--accent)' }}
        >
          {project.projectType}
        </span>
      )}

      {/* Summary */}
      {project.portfolioSummary ? (
        <p className="text-xs leading-relaxed line-clamp-3 flex-1" style={{ color: 'var(--text-secondary)' }}>
          {project.portfolioSummary}
        </p>
      ) : (
        <p className="text-xs italic flex-1" style={{ color: 'var(--text-muted)' }}>No summary available</p>
      )}

      {/* Tags */}
      {tags.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {tags.slice(0, 6).map(tag => (
            <span
              key={tag}
              className="px-2 py-0.5 rounded-md text-[11px] font-mono-dm"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-muted)' }}
            >
              {tag}
            </span>
          ))}
        </div>
      )}

      {/* Footer */}
      <div
        className="flex items-center justify-between pt-3"
        style={{ borderTop: '1px solid var(--border)' }}
      >
        <span className="text-[11px] font-mono-dm" style={{ color: 'var(--text-muted)' }}>
          {project.analyzedAt ? `analyzed ${timeAgo(project.analyzedAt)}` : 'recently analyzed'}
        </span>
        <button
          onClick={() => navigate(`/results/${project.repoId}`)}
          className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-medium transition-all duration-150"
          style={{
            background: 'var(--bg-surface)',
            border: '1px solid var(--border)',
            color: 'var(--text-secondary)',
          }}
          onMouseEnter={e => {
            (e.currentTarget as HTMLElement).style.background = 'var(--accent-glow)';
            (e.currentTarget as HTMLElement).style.borderColor = 'var(--accent-border)';
            (e.currentTarget as HTMLElement).style.color = 'var(--accent)';
          }}
          onMouseLeave={e => {
            (e.currentTarget as HTMLElement).style.background = 'var(--bg-surface)';
            (e.currentTarget as HTMLElement).style.borderColor = 'var(--border)';
            (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)';
          }}
        >
          View &amp; Edit
          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
    </div>
  );
}

export default function WorkspacePage() {
  const navigate = useNavigate();

  const { data: projects = [], isLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.projects.list(),
    staleTime: 60_000,
  });

  return (
    <Layout maxWidth="xl" navLinks={[
      { label: 'Dashboard', to: '/dashboard' },
      { label: 'Queue', to: '/status' },
    ]}>
      {/* Header */}
      <div className="flex items-start justify-between mb-8 animate-fade-up">
        <div>
          <h1 className="font-display text-3xl mb-1" style={{ color: 'var(--text-primary)' }}>
            My Workspace
          </h1>
          <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
            {isLoading
              ? 'Loading…'
              : `${projects.length} analyzed repositor${projects.length !== 1 ? 'ies' : 'y'}`}
          </p>
        </div>
        <button
          onClick={() => navigate('/dashboard')}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm transition-all duration-150 shrink-0"
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
          + Analyze more
        </button>
      </div>

      {/* Skeletons */}
      {isLoading && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
          {[...Array(4)].map((_, i) => <SkeletonCard key={i} />)}
        </div>
      )}

      {/* Empty */}
      {!isLoading && projects.length === 0 && (
        <div className="flex flex-col items-center justify-center py-24 animate-fade-up">
          <div
            className="w-12 h-12 rounded-xl flex items-center justify-center mb-4"
            style={{ background: 'var(--bg-card)', border: '1px solid var(--border)' }}
          >
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5} style={{ color: 'var(--text-muted)' }}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
          </div>
          <p className="font-medium mb-1.5" style={{ color: 'var(--text-primary)' }}>No analyzed projects yet</p>
          <p className="text-sm mb-6" style={{ color: 'var(--text-muted)' }}>Go to your Dashboard, select repos, and run an analysis.</p>
          <button
            onClick={() => navigate('/dashboard')}
            className="px-5 py-2.5 rounded-xl text-sm font-semibold transition-all"
            style={{ background: 'var(--accent)', color: '#0a0906' }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.filter = 'brightness(1.1)'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.filter = 'none'; }}
          >
            Go to Dashboard
          </button>
        </div>
      )}

      {/* Grid */}
      {!isLoading && projects.length > 0 && (
        <div className="grid grid-cols-1 md:grid-cols-2 gap-3 animate-fade-up stagger-1">
          {projects.map(p => <ProjectCard key={p.repoId} project={p} />)}
        </div>
      )}
    </Layout>
  );
}
