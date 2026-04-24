import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api, BACKEND_URL } from '../lib/api';
import type { AnalysisJob, JobStatus } from '../lib/api';
import { useCurrentUser } from '../hooks/useCurrentUser';

// ── Status config ─────────────────────────────────────────────────────────────

const STATUS_CONFIG: Record<JobStatus, { label: string; color: string; icon: React.ReactNode }> = {
  QUEUED: {
    label: 'Queued',
    color: 'text-yellow-400 bg-yellow-950 border-yellow-800/50',
    icon: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
      </svg>
    ),
  },
  PROCESSING: {
    label: 'Processing',
    color: 'text-violet-400 bg-violet-950 border-violet-800/50',
    icon: <div className="w-4 h-4 border-2 border-violet-400 border-t-transparent rounded-full animate-spin" />,
  },
  RETRYING: {
    label: 'Retrying',
    color: 'text-orange-400 bg-orange-950 border-orange-800/50',
    icon: (
      <svg className="w-4 h-4 animate-spin" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
      </svg>
    ),
  },
  COMPLETED: {
    label: 'Completed',
    color: 'text-green-400 bg-green-950 border-green-800/50',
    icon: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
      </svg>
    ),
  },
  FAILED: {
    label: 'Failed',
    color: 'text-red-400 bg-red-950 border-red-800/50',
    icon: (
      <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
        <path strokeLinecap="round" strokeLinejoin="round" d="M6 18L18 6M6 6l12 12" />
      </svg>
    ),
  },
};

// ── Job card ──────────────────────────────────────────────────────────────────

function JobCard({ job }: { job: AnalysisJob }) {
  const navigate = useNavigate();
  const cfg = STATUS_CONFIG[job.status];

  const elapsed = (() => {
    if (!job.startedAt) return null;
    const end = job.completedAt ? new Date(job.completedAt) : new Date();
    const secs = Math.floor((end.getTime() - new Date(job.startedAt).getTime()) / 1000);
    return secs < 60 ? `${secs}s` : `${Math.floor(secs / 60)}m ${secs % 60}s`;
  })();

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
      <div className="flex items-start justify-between gap-4">
        <div className="min-w-0 flex-1">
          <p className="text-white font-semibold text-sm truncate">{job.repoName}</p>
          {job.repoFullName && (
            <p className="text-gray-600 text-xs mt-0.5 truncate">{job.repoFullName}</p>
          )}
        </div>
        <span className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium border shrink-0 ${cfg.color}`}>
          {cfg.icon}
          {cfg.label}
        </span>
      </div>

      {/* Progress detail */}
      <div className="mt-3 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-600">
        {elapsed && <span>Elapsed: {elapsed}</span>}
        {job.status === 'PROCESSING' && (
          <span className="text-violet-500">Extracting signals & generating content…</span>
        )}
        {job.status === 'RETRYING' && (
          <span className="text-orange-500">Retrying after transient error…</span>
        )}
      </div>

      {/* Error message */}
      {job.status === 'FAILED' && job.errorMessage && (
        <p className="mt-3 text-xs text-red-400 bg-red-950/50 border border-red-900/50 rounded-lg px-3 py-2 font-mono leading-relaxed">
          {job.errorMessage}
        </p>
      )}

      {/* Actions */}
      {job.status === 'COMPLETED' && (
        <div className="mt-4">
          <button
            onClick={() => navigate(`/results/${job.repoId}`)}
            className="w-full px-4 py-2 bg-violet-600 hover:bg-violet-500 rounded-lg text-sm font-semibold text-white transition-colors"
          >
            View Results →
          </button>
        </div>
      )}
    </div>
  );
}

// ── Page ──────────────────────────────────────────────────────────────────────

export default function AnalysisStatusPage() {
  const navigate = useNavigate();
  const { data: user } = useCurrentUser();

  const { data: jobs = [] } = useQuery({
    queryKey: ['jobs'],
    queryFn: () => api.jobs.list(),
    // Poll every 2s while any job is active; stop when all are terminal
    refetchInterval: (query) => {
      const data = query.state.data as AnalysisJob[] | undefined;
      return data?.some(j => j.isActive) ? 2000 : false;
    },
    staleTime: 0,
  });

  const activeCount = jobs.filter(j => j.isActive).length;
  const completedCount = jobs.filter(j => j.status === 'COMPLETED').length;
  const failedCount = jobs.filter(j => j.status === 'FAILED').length;
  const allDone = jobs.length > 0 && activeCount === 0;

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
            onClick={() => { window.location.href = `${BACKEND_URL}/auth/logout`; }}
            className="text-sm text-gray-400 hover:text-white transition-colors"
          >
            Log out
          </button>
        </div>
      </header>

      <main className="max-w-2xl mx-auto px-6 py-8">
        {/* Title */}
        <div className="mb-6">
          <div className="flex items-center gap-3 mb-1">
            <h2 className="text-2xl font-bold">Analysis Jobs</h2>
            {activeCount > 0 && (
              <div className="w-2 h-2 rounded-full bg-violet-500 animate-pulse" />
            )}
          </div>
          <p className="text-gray-500 text-sm">
            {activeCount > 0
              ? `${activeCount} job${activeCount > 1 ? 's' : ''} running…`
              : allDone
              ? `All done — ${completedCount} completed${failedCount > 0 ? `, ${failedCount} failed` : ''}`
              : 'No recent jobs'}
          </p>
        </div>

        {/* All-done banner */}
        {allDone && completedCount > 0 && (
          <div className="mb-6 flex items-center justify-between gap-4 px-4 py-3 bg-green-950/50 border border-green-800/50 rounded-xl">
            <div className="flex items-center gap-2 text-green-400 text-sm font-medium">
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M5 13l4 4L19 7" />
              </svg>
              Analysis complete
            </div>
            <button
              onClick={() => navigate('/workspace')}
              className="px-3 py-1.5 bg-green-700 hover:bg-green-600 rounded-lg text-xs font-semibold text-white transition-colors"
            >
              Go to Workspace →
            </button>
          </div>
        )}

        {/* Job cards */}
        {jobs.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-gray-500 mb-4">No analysis jobs found.</p>
            <button
              onClick={() => navigate('/dashboard')}
              className="px-5 py-2.5 bg-violet-600 hover:bg-violet-500 rounded-lg text-sm font-semibold transition-colors"
            >
              Go to Dashboard
            </button>
          </div>
        ) : (
          <div className="space-y-3">
            {jobs.map(job => <JobCard key={job.jobId} job={job} />)}
          </div>
        )}

        {/* Nav links */}
        <div className="mt-6 flex gap-4 text-sm text-gray-600">
          <button onClick={() => navigate('/dashboard')} className="hover:text-gray-300 transition-colors">
            ← Dashboard
          </button>
          <button onClick={() => navigate('/workspace')} className="hover:text-gray-300 transition-colors">
            Workspace →
          </button>
        </div>
      </main>
    </div>
  );
}
