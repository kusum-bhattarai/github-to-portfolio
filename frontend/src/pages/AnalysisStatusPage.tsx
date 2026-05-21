import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { AnalysisJob, JobStatus } from '../lib/api';
import Layout from '../components/Layout';

const STATUS_STYLE: Record<JobStatus, { label: string; dot: string; text: string; bg: string; border: string }> = {
  QUEUED:     { label: 'Queued',     dot: '#eab308', text: '#eab308', bg: 'rgba(234,179,8,0.08)',   border: 'rgba(234,179,8,0.2)'   },
  PROCESSING: { label: 'Processing', dot: 'var(--accent)', text: 'var(--accent)', bg: 'var(--accent-glow)', border: 'var(--accent-border)' },
  RETRYING:   { label: 'Retrying',   dot: '#fb923c', text: '#fb923c', bg: 'rgba(251,146,60,0.08)',  border: 'rgba(251,146,60,0.2)'  },
  COMPLETED:  { label: 'Completed',  dot: '#4ade80', text: '#4ade80', bg: 'rgba(74,222,128,0.08)',  border: 'rgba(74,222,128,0.2)'  },
  FAILED:     { label: 'Failed',     dot: '#f87171', text: '#f87171', bg: 'rgba(248,113,113,0.08)', border: 'rgba(248,113,113,0.2)' },
};

function elapsed(job: AnalysisJob): string | null {
  if (!job.startedAt) return null;
  const end = job.completedAt ? new Date(job.completedAt) : new Date();
  const secs = Math.floor((end.getTime() - new Date(job.startedAt).getTime()) / 1000);
  return secs < 60 ? `${secs}s` : `${Math.floor(secs / 60)}m ${secs % 60}s`;
}

function JobRow({ job }: { job: AnalysisJob }) {
  const navigate = useNavigate();
  const s = STATUS_STYLE[job.status];
  const dur = elapsed(job);

  return (
    <div
      className="rounded-xl p-4 transition-all duration-150"
      style={{ background: 'var(--bg-card)', border: '1px solid var(--border)' }}
    >
      <div className="flex items-center justify-between gap-3">
        <div className="min-w-0 flex-1">
          <p className="text-sm font-medium truncate" style={{ color: 'var(--text-primary)' }}>
            {job.repoName}
          </p>
          {job.repoFullName && (
            <p className="text-xs mt-0.5 truncate font-mono-dm" style={{ color: 'var(--text-muted)' }}>
              {job.repoFullName}
            </p>
          )}
        </div>

        <span
          className="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-xs font-medium shrink-0"
          style={{ background: s.bg, border: `1px solid ${s.border}`, color: s.text }}
        >
          {job.status === 'PROCESSING' ? (
            <span
              className="w-2 h-2 rounded-full animate-pulse"
              style={{ background: s.dot }}
            />
          ) : (
            <span className="w-2 h-2 rounded-full" style={{ background: s.dot }} />
          )}
          {s.label}
        </span>
      </div>

      {/* Detail row */}
      {(dur || job.status === 'PROCESSING' || job.status === 'RETRYING') && (
        <div className="mt-2.5 flex items-center gap-3 text-xs" style={{ color: 'var(--text-muted)' }}>
          {dur && <span className="font-mono-dm">{dur}</span>}
          {job.status === 'PROCESSING' && (
            <span style={{ color: 'var(--accent-dim)' }}>Extracting signals &amp; generating content…</span>
          )}
          {job.status === 'RETRYING' && (
            <span style={{ color: '#fb923c' }}>Retrying after transient error…</span>
          )}
        </div>
      )}

      {job.status === 'FAILED' && job.errorMessage && (
        <p
          className="mt-3 text-xs px-3 py-2 rounded-lg font-mono-dm leading-relaxed"
          style={{ background: 'rgba(248,113,113,0.06)', border: '1px solid rgba(248,113,113,0.15)', color: '#f87171' }}
        >
          {job.errorMessage}
        </p>
      )}

      {job.status === 'COMPLETED' && (
        <button
          onClick={() => navigate(`/results/${job.repoId}`)}
          className="mt-3 w-full py-1.5 rounded-lg text-xs font-semibold transition-all duration-150"
          style={{ background: 'var(--accent-glow)', border: '1px solid var(--accent-border)', color: 'var(--accent)' }}
          onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(240,160,48,0.15)'; }}
          onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'var(--accent-glow)'; }}
        >
          View results →
        </button>
      )}
    </div>
  );
}

export default function AnalysisStatusPage() {
  const navigate = useNavigate();

  const { data: jobs = [] } = useQuery({
    queryKey: ['jobs'],
    queryFn: () => api.jobs.list(),
    refetchInterval: (query) => {
      const data = query.state.data as AnalysisJob[] | undefined;
      return data?.some(j => j.isActive) ? 2000 : false;
    },
    staleTime: 0,
  });

  const activeCount   = jobs.filter(j => j.isActive).length;
  const completedCount = jobs.filter(j => j.status === 'COMPLETED').length;
  const failedCount    = jobs.filter(j => j.status === 'FAILED').length;
  const allDone        = jobs.length > 0 && activeCount === 0;

  return (
    <Layout maxWidth="md" navLinks={[{ label: '← Dashboard', to: '/dashboard' }]}>
      {/* Page header */}
      <div className="mb-7 animate-fade-up">
        <div className="flex items-center gap-2.5 mb-1">
          <h1 className="font-display text-3xl" style={{ color: 'var(--text-primary)' }}>
            Analysis Queue
          </h1>
          {activeCount > 0 && (
            <span
              className="w-2.5 h-2.5 rounded-full animate-pulse"
              style={{ background: 'var(--accent)' }}
            />
          )}
        </div>
        <p className="text-sm" style={{ color: 'var(--text-muted)' }}>
          {activeCount > 0
            ? `${activeCount} job${activeCount !== 1 ? 's' : ''} running…`
            : allDone
            ? `All done — ${completedCount} completed${failedCount > 0 ? `, ${failedCount} failed` : ''}`
            : 'No recent jobs'}
        </p>
      </div>

      {/* All-done banner */}
      {allDone && completedCount > 0 && (
        <div
          className="flex items-center justify-between gap-4 px-4 py-3 rounded-xl mb-6 animate-fade-up stagger-1"
          style={{ background: 'rgba(74,222,128,0.06)', border: '1px solid rgba(74,222,128,0.15)' }}
        >
          <span className="text-sm font-medium" style={{ color: '#4ade80' }}>
            Analysis complete
          </span>
          <button
            onClick={() => navigate('/workspace')}
            className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all"
            style={{ background: 'rgba(74,222,128,0.12)', border: '1px solid rgba(74,222,128,0.2)', color: '#4ade80' }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(74,222,128,0.2)'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.background = 'rgba(74,222,128,0.12)'; }}
          >
            Go to Workspace →
          </button>
        </div>
      )}

      {/* Jobs */}
      {jobs.length === 0 ? (
        <div className="text-center py-24 animate-fade-up">
          <p className="text-sm mb-5" style={{ color: 'var(--text-muted)' }}>No analysis jobs found.</p>
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
      ) : (
        <div className="space-y-2.5 animate-fade-up stagger-2">
          {jobs.map(job => <JobRow key={job.jobId} job={job} />)}
        </div>
      )}

      {/* Nav */}
      <div className="mt-8 flex gap-4 text-xs" style={{ color: 'var(--text-muted)' }}>
        <button
          onClick={() => navigate('/dashboard')}
          className="transition-colors"
          onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)'; }}
          onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'; }}
        >
          ← Dashboard
        </button>
        <button
          onClick={() => navigate('/workspace')}
          className="transition-colors"
          onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)'; }}
          onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'; }}
        >
          Workspace →
        </button>
      </div>
    </Layout>
  );
}
