import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api, BACKEND_URL } from '../lib/api';
import type { ProjectSummary } from '../lib/api';
import { useCurrentUser } from '../hooks/useCurrentUser';

// ── Utilities ────────────────────────────────────────────────────────────────

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

const LANG_COLOR: Record<string, string> = {
  TypeScript: 'bg-blue-500',
  JavaScript: 'bg-yellow-400',
  Python:     'bg-green-500',
  Java:       'bg-orange-500',
  Go:         'bg-cyan-400',
  Rust:       'bg-orange-600',
  Ruby:       'bg-red-500',
  'C++':      'bg-pink-500',
  'C#':       'bg-purple-500',
  Kotlin:     'bg-violet-500',
  Swift:      'bg-orange-400',
};

function langDot(lang: string | null) {
  if (!lang) return null;
  const color = LANG_COLOR[lang] ?? 'bg-gray-500';
  return (
    <span className="flex items-center gap-1.5 text-xs text-gray-400">
      <span className={`w-2 h-2 rounded-full ${color} shrink-0`} />
      {lang}
    </span>
  );
}

// ── Skeleton ─────────────────────────────────────────────────────────────────

function SkeletonCard() {
  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-5 space-y-3 animate-pulse">
      <div className="flex items-start justify-between gap-2">
        <div className="h-4 bg-gray-800 rounded w-2/5" />
        <div className="h-4 bg-gray-800 rounded w-16" />
      </div>
      <div className="h-3 bg-gray-800 rounded w-1/3" />
      <div className="space-y-2 pt-1">
        <div className="h-3 bg-gray-800 rounded w-full" />
        <div className="h-3 bg-gray-800 rounded w-5/6" />
        <div className="h-3 bg-gray-800 rounded w-4/6" />
      </div>
      <div className="flex gap-2 pt-1">
        <div className="h-5 bg-gray-800 rounded-full w-16" />
        <div className="h-5 bg-gray-800 rounded-full w-20" />
      </div>
      <div className="flex items-center justify-between pt-2 border-t border-gray-800">
        <div className="h-3 bg-gray-800 rounded w-24" />
        <div className="h-7 bg-gray-800 rounded-lg w-24" />
      </div>
    </div>
  );
}

// ── Project Card ─────────────────────────────────────────────────────────────

function ProjectCard({ project }: { project: ProjectSummary }) {
  const navigate = useNavigate();
  const tags = project.projectTags
    ? project.projectTags.split(',').map(t => t.trim()).filter(Boolean)
    : [];

  return (
    <div className="group bg-gray-900 border border-gray-800 hover:border-gray-700 rounded-xl p-5 flex flex-col gap-4 transition-colors duration-150">
      {/* Top row */}
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-1.5 min-w-0">
          <h3 className="text-white font-semibold text-sm leading-tight truncate">
            {project.repoName}
          </h3>
          {langDot(project.primaryLanguage)}
        </div>
        <span className="flex items-center gap-1 text-xs text-gray-500 shrink-0">
          <svg className="w-3.5 h-3.5 text-yellow-500" fill="currentColor" viewBox="0 0 20 20">
            <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
          </svg>
          {project.stars.toLocaleString()}
        </span>
      </div>

      {/* Project type badge */}
      {project.projectType && (
        <span className="self-start px-2 py-0.5 rounded-full text-[11px] font-medium bg-violet-950 text-violet-300 border border-violet-800/50">
          {project.projectType}
        </span>
      )}

      {/* Summary */}
      {project.portfolioSummary ? (
        <p className="text-gray-400 text-xs leading-relaxed line-clamp-3 flex-1">
          {project.portfolioSummary}
        </p>
      ) : (
        <p className="text-gray-600 text-xs italic flex-1">No summary available</p>
      )}

      {/* Tech tags */}
      {tags.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {tags.slice(0, 6).map(tag => (
            <span key={tag} className="px-2 py-0.5 bg-gray-800 border border-gray-700/50 rounded-md text-[11px] text-gray-400">
              {tag}
            </span>
          ))}
        </div>
      )}

      {/* Footer */}
      <div className="flex items-center justify-between pt-3 border-t border-gray-800">
        <span className="text-[11px] text-gray-600">
          {project.analyzedAt ? `Analyzed ${timeAgo(project.analyzedAt)}` : 'Recently analyzed'}
        </span>
        <button
          onClick={() => navigate(`/results/${project.repoId}`)}
          className="flex items-center gap-1.5 px-3 py-1.5 bg-gray-800 hover:bg-violet-600 border border-gray-700 hover:border-violet-500 rounded-lg text-xs font-medium text-gray-300 hover:text-white transition-all duration-150"
        >
          View & Edit
          <svg className="w-3 h-3" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
            <path strokeLinecap="round" strokeLinejoin="round" d="M9 5l7 7-7 7" />
          </svg>
        </button>
      </div>
    </div>
  );
}

// ── Page ─────────────────────────────────────────────────────────────────────

export default function WorkspacePage() {
  const navigate = useNavigate();
  const { data: user } = useCurrentUser();

  const { data: projects = [], isLoading } = useQuery({
    queryKey: ['projects'],
    queryFn: () => api.projects.list(),
    staleTime: 60_000,
  });

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
            onClick={() => navigate('/dashboard')}
            className="text-sm text-gray-400 hover:text-white transition-colors"
          >
            Dashboard
          </button>
          <button
            onClick={() => navigate('/status')}
            className="text-sm text-gray-400 hover:text-white transition-colors"
          >
            Queue
          </button>
          <button
            onClick={() => { window.location.href = `${BACKEND_URL}/auth/logout`; }}
            className="text-sm text-gray-400 hover:text-white transition-colors"
          >
            Log out
          </button>
        </div>
      </header>

      <main className="max-w-5xl mx-auto px-6 py-8">
        {/* Title area */}
        <div className="flex items-start justify-between mb-8">
          <div>
            <h2 className="text-2xl font-bold text-white">My Workspace</h2>
            <p className="text-gray-500 text-sm mt-1">
              {isLoading ? 'Loading…' : `${projects.length} analyzed repositor${projects.length !== 1 ? 'ies' : 'y'}`}
            </p>
          </div>
          <button
            onClick={() => navigate('/dashboard')}
            className="flex items-center gap-2 px-4 py-2 bg-gray-900 hover:bg-gray-800 border border-gray-800 rounded-lg text-sm text-gray-300 hover:text-white transition-colors"
          >
            <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M3 7h18M3 12h18M3 17h18" />
            </svg>
            Dashboard
          </button>
        </div>

        {/* Loading */}
        {isLoading && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {[...Array(4)].map((_, i) => <SkeletonCard key={i} />)}
          </div>
        )}

        {/* Empty state */}
        {!isLoading && projects.length === 0 && (
          <div className="flex flex-col items-center justify-center py-24">
            <div className="w-12 h-12 rounded-xl bg-gray-900 border border-gray-800 flex items-center justify-center mb-4">
              <svg className="w-6 h-6 text-gray-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={1.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
              </svg>
            </div>
            <p className="text-gray-400 font-medium mb-1">No analyzed projects yet</p>
            <p className="text-gray-600 text-sm mb-6">Go to your Dashboard, select repositories, and run an analysis.</p>
            <button
              onClick={() => navigate('/dashboard')}
              className="px-5 py-2.5 bg-violet-600 hover:bg-violet-500 rounded-lg text-sm font-semibold transition-colors"
            >
              Go to Dashboard
            </button>
          </div>
        )}

        {/* Project grid */}
        {!isLoading && projects.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            {projects.map(p => <ProjectCard key={p.repoId} project={p} />)}
          </div>
        )}
      </main>
    </div>
  );
}
