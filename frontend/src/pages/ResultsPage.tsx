import { useState } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { AnalysisJob, ContentBlock, ContentType } from '../lib/api';
import Layout from '../components/Layout';

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button
      onClick={copy}
      className="text-xs transition-colors"
      style={{ color: copied ? 'var(--green)' : 'var(--text-muted)' }}
      onMouseEnter={e => { if (!copied) (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)'; }}
      onMouseLeave={e => { if (!copied) (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'; }}
    >
      {copied ? '✓ Copied' : 'Copy'}
    </button>
  );
}

const CONTENT_LABELS: Record<ContentType, string> = {
  PORTFOLIO_SUMMARY: 'Portfolio Summary',
  RESUME_BULLETS:    'Resume Bullets',
  TECH_STACK:        'Tech Stack',
  PROJECT_TAGS:      'Project Tags',
  ONE_SENTENCE_PITCH:'One-Sentence Pitch',
  TALKING_POINTS:    'Talking Points',
  INTERVIEW_STORY:   'Interview Story',
};

const EDIT_ROWS: Partial<Record<ContentType, number>> = {
  PORTFOLIO_SUMMARY: 4,
  INTERVIEW_STORY:   14,
  TALKING_POINTS:    10,
  RESUME_BULLETS:    8,
};

function renderContent(block: ContentBlock) {
  const text = block.editedText ?? block.generatedText;

  if (block.contentType === 'RESUME_BULLETS' || block.contentType === 'TALKING_POINTS') {
    return (
      <ul className="space-y-2">
        {text.split('\n').filter(Boolean).map((b, i) => (
          <li key={i} className="flex gap-2.5 text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
            <span className="shrink-0 mt-1 w-1.5 h-1.5 rounded-full" style={{ background: 'var(--accent)' }} />
            <span>{b.replace(/^[-•]\s*/, '')}</span>
          </li>
        ))}
      </ul>
    );
  }
  if (block.contentType === 'TECH_STACK' || block.contentType === 'PROJECT_TAGS') {
    return (
      <div className="flex flex-wrap gap-2">
        {text.split(',').map(s => s.trim()).filter(Boolean).map(item => (
          <span
            key={item}
            className="px-2.5 py-1 rounded-lg text-xs font-mono-dm"
            style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}
          >
            {item}
          </span>
        ))}
      </div>
    );
  }
  if (block.contentType === 'ONE_SENTENCE_PITCH') {
    return (
      <p
        className="text-base font-medium leading-relaxed font-display italic"
        style={{ color: 'var(--text-primary)' }}
      >
        "{text}"
      </p>
    );
  }
  if (block.contentType === 'INTERVIEW_STORY') {
    return (
      <div className="space-y-4">
        {text.split(/\n\n+/).filter(Boolean).map((section, i) => {
          const colon = section.indexOf(':');
          if (colon !== -1) {
            const label = section.slice(0, colon).trim();
            const body  = section.slice(colon + 1).trim();
            return (
              <div key={i}>
                <p
                  className="text-[10px] font-semibold uppercase tracking-widest mb-1 font-mono-dm"
                  style={{ color: 'var(--accent)' }}
                >
                  {label}
                </p>
                <p className="text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
                  {body}
                </p>
              </div>
            );
          }
          return (
            <p key={i} className="text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
              {section}
            </p>
          );
        })}
      </div>
    );
  }
  return (
    <p className="text-sm leading-relaxed" style={{ color: 'var(--text-secondary)' }}>
      {text}
    </p>
  );
}

function ContentCard({
  block, repoId, onSaved,
}: {
  block: ContentBlock;
  repoId: string;
  onSaved: (id: string, text: string) => void;
}) {
  const displayText = block.editedText ?? block.generatedText;
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(displayText);

  const saveMutation = useMutation({
    mutationFn: (text: string) => api.projects.saveEdit(repoId, block.id, text),
    onSuccess: () => { onSaved(block.id, draft); setEditing(false); },
  });

  return (
    <div
      className="rounded-xl p-5 transition-all duration-150"
      style={{ background: 'var(--bg-card)', border: '1px solid var(--border)' }}
    >
      {/* Header */}
      <div className="flex items-center justify-between mb-4">
        <div className="flex items-center gap-2">
          <span
            className="text-xs font-semibold uppercase tracking-widest font-mono-dm"
            style={{ color: 'var(--text-muted)' }}
          >
            {CONTENT_LABELS[block.contentType] ?? block.contentType}
          </span>
          {block.isEdited && (
            <span
              className="px-1.5 py-0.5 text-[10px] font-medium rounded font-mono-dm"
              style={{ background: 'var(--accent-glow)', border: '1px solid var(--accent-border)', color: 'var(--accent)' }}
            >
              edited
            </span>
          )}
        </div>
        {!editing && (
          <div className="flex items-center gap-3">
            <CopyButton text={displayText} />
            <button
              onClick={() => { setDraft(displayText); setEditing(true); }}
              className="text-xs transition-colors"
              style={{ color: 'var(--text-muted)' }}
              onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'; }}
            >
              Edit
            </button>
          </div>
        )}
      </div>

      {editing ? (
        <div className="space-y-3">
          <textarea
            value={draft}
            onChange={e => setDraft(e.target.value)}
            rows={EDIT_ROWS[block.contentType] ?? 6}
            className="w-full px-3 py-2.5 rounded-lg text-sm leading-relaxed outline-none resize-y transition-all font-mono-dm"
            style={{
              background: 'var(--bg-surface)',
              border: '1px solid var(--border-light)',
              color: 'var(--text-primary)',
            }}
            onFocus={e => { (e.currentTarget as HTMLElement).style.borderColor = 'var(--accent-border)'; }}
            onBlur={e => { (e.currentTarget as HTMLElement).style.borderColor = 'var(--border-light)'; }}
            autoFocus
          />
          <div className="flex items-center gap-2">
            <button
              onClick={() => saveMutation.mutate(draft)}
              disabled={saveMutation.isPending || draft.trim() === displayText.trim()}
              className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all disabled:opacity-40"
              style={{ background: 'var(--accent)', color: '#0a0906' }}
              onMouseEnter={e => { (e.currentTarget as HTMLElement).style.filter = 'brightness(1.1)'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLElement).style.filter = 'none'; }}
            >
              {saveMutation.isPending ? 'Saving…' : 'Save'}
            </button>
            <button
              onClick={() => { setDraft(displayText); setEditing(false); }}
              disabled={saveMutation.isPending}
              className="px-3 py-1.5 rounded-lg text-xs font-medium transition-all"
              style={{ background: 'var(--bg-surface)', border: '1px solid var(--border)', color: 'var(--text-secondary)' }}
              onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)'; }}
            >
              Cancel
            </button>
            {block.isEdited && (
              <button
                onClick={() => { setDraft(block.generatedText); saveMutation.mutate(block.generatedText); }}
                disabled={saveMutation.isPending}
                className="ml-auto text-xs transition-colors"
                style={{ color: 'var(--text-muted)' }}
                onMouseEnter={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)'; }}
                onMouseLeave={e => { (e.currentTarget as HTMLElement).style.color = 'var(--text-muted)'; }}
              >
                Reset to original
              </button>
            )}
          </div>
          {saveMutation.isError && (
            <p className="text-xs" style={{ color: 'var(--red)' }}>Failed to save. Please try again.</p>
          )}
        </div>
      ) : renderContent(block)}
    </div>
  );
}

type View = 'resume' | 'interview';
const RESUME_TYPES: ContentType[]   = ['PORTFOLIO_SUMMARY', 'RESUME_BULLETS', 'TECH_STACK', 'PROJECT_TAGS'];
const INTERVIEW_TYPES: ContentType[] = ['ONE_SENTENCE_PITCH', 'TALKING_POINTS', 'INTERVIEW_STORY'];
const RESUME_ORDER   = ['PORTFOLIO_SUMMARY', 'RESUME_BULLETS', 'TECH_STACK', 'PROJECT_TAGS'];
const INTERVIEW_ORDER = ['ONE_SENTENCE_PITCH', 'TALKING_POINTS', 'INTERVIEW_STORY'];

export default function ResultsPage() {
  const { repoId } = useParams<{ repoId: string }>();
  const queryClient = useQueryClient();
  const [view, setView] = useState<View>('resume');

  const { data: content = [], isLoading } = useQuery({
    queryKey: ['content', repoId],
    queryFn: () => api.analysis.getContent(repoId!),
    enabled: !!repoId,
  });

  const [activeJobId, setActiveJobId] = useState<string | null>(null);

  useQuery({
    queryKey: ['reanalyzeJob', activeJobId],
    queryFn: () => api.jobs.get(activeJobId!),
    enabled: !!activeJobId,
    refetchInterval: (query) => {
      const job = query.state.data as AnalysisJob | undefined;
      if (!job || job.isActive) return 2000;
      if (job.status === 'COMPLETED') {
        queryClient.invalidateQueries({ queryKey: ['content', repoId] });
        queryClient.invalidateQueries({ queryKey: ['projects'] });
        setActiveJobId(null);
      } else if (job.status === 'FAILED') {
        setActiveJobId(null);
      }
      return false;
    },
  });

  const reanalyzeMutation = useMutation({
    mutationFn: () => api.analysis.analyze(repoId!),
    onSuccess: (job) => setActiveJobId(job.jobId),
  });

  const handleSaved = (contentId: string, newText: string) => {
    queryClient.setQueryData<ContentBlock[]>(['content', repoId], old =>
      old?.map(b => b.id === contentId ? { ...b, editedText: newText, isEdited: true } : b)
    );
    queryClient.invalidateQueries({ queryKey: ['projects'] });
  };

  const activeTypes = view === 'resume' ? RESUME_TYPES : INTERVIEW_TYPES;
  const activeOrder = view === 'resume' ? RESUME_ORDER : INTERVIEW_ORDER;
  const visibleBlocks = content
    .filter(b => activeTypes.includes(b.contentType))
    .sort((a, b) => activeOrder.indexOf(a.contentType) - activeOrder.indexOf(b.contentType));
  const hasInterviewContent = content.some(b => INTERVIEW_TYPES.includes(b.contentType));
  const editedCount = content.filter(b => b.isEdited).length;
  const isAnalyzing = reanalyzeMutation.isPending || !!activeJobId;

  return (
    <Layout maxWidth="lg" navLinks={[{ label: '← Workspace', to: '/workspace' }]}>
      {/* Page header */}
      <div className="flex items-center justify-between mb-7 animate-fade-up">
        <div className="flex items-center gap-3">
          <h1 className="font-display text-3xl" style={{ color: 'var(--text-primary)' }}>
            Generated Content
          </h1>
          {editedCount > 0 && (
            <span
              className="px-2 py-0.5 text-xs font-mono-dm rounded-full"
              style={{ background: 'var(--accent-glow)', border: '1px solid var(--accent-border)', color: 'var(--accent)' }}
            >
              {editedCount} edited
            </span>
          )}
        </div>
        <button
          onClick={() => reanalyzeMutation.mutate()}
          disabled={isAnalyzing}
          className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-medium transition-all duration-150 disabled:opacity-50"
          style={{
            background: 'var(--bg-card)',
            border: '1px solid var(--border)',
            color: 'var(--text-secondary)',
          }}
          onMouseEnter={e => {
            if (!isAnalyzing) {
              (e.currentTarget as HTMLElement).style.borderColor = 'var(--border-light)';
              (e.currentTarget as HTMLElement).style.color = 'var(--text-primary)';
            }
          }}
          onMouseLeave={e => {
            (e.currentTarget as HTMLElement).style.borderColor = 'var(--border)';
            (e.currentTarget as HTMLElement).style.color = 'var(--text-secondary)';
          }}
        >
          <svg
            className={`w-4 h-4 ${isAnalyzing ? 'animate-spin' : ''}`}
            fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
          >
            <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
          </svg>
          {isAnalyzing ? 'Analyzing…' : 'Re-analyze'}
        </button>
      </div>

      {/* View toggle */}
      {content.length > 0 && (
        <div
          className="flex gap-1 p-1 rounded-xl w-fit mb-7 animate-fade-up stagger-1"
          style={{ background: 'var(--bg-card)', border: '1px solid var(--border)' }}
        >
          {(['resume', 'interview'] as View[]).map(v => (
            <button
              key={v}
              onClick={() => setView(v)}
              className="px-4 py-1.5 rounded-lg text-sm font-medium transition-all duration-150 capitalize"
              style={{
                background: view === v ? 'var(--accent)' : 'transparent',
                color: view === v ? '#0a0906' : 'var(--text-muted)',
              }}
            >
              {v}
              {v === 'interview' && !hasInterviewContent && (
                <span className="ml-1.5 text-[10px] opacity-60">(re-analyze)</span>
              )}
            </button>
          ))}
        </div>
      )}

      {/* Content */}
      {isLoading ? (
        <div className="space-y-3">
          {[...Array(4)].map((_, i) => (
            <div key={i} className="rounded-xl h-28 skeleton" style={{ border: '1px solid var(--border)' }} />
          ))}
        </div>
      ) : content.length === 0 ? (
        <div className="text-center py-24 animate-fade-up">
          <p className="text-sm mb-5" style={{ color: 'var(--text-muted)' }}>No content generated yet.</p>
          <button
            onClick={() => reanalyzeMutation.mutate()}
            disabled={isAnalyzing}
            className="px-6 py-3 rounded-xl font-semibold text-sm transition-all disabled:opacity-50"
            style={{ background: 'var(--accent)', color: '#0a0906' }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.filter = 'brightness(1.1)'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.filter = 'none'; }}
          >
            {isAnalyzing ? 'Analyzing…' : 'Analyze Now'}
          </button>
        </div>
      ) : visibleBlocks.length === 0 ? (
        <div className="text-center py-16 animate-fade-up">
          <p className="text-sm mb-4" style={{ color: 'var(--text-muted)' }}>No interview content yet.</p>
          <button
            onClick={() => reanalyzeMutation.mutate()}
            disabled={isAnalyzing}
            className="px-5 py-2 rounded-lg text-sm font-semibold transition-all disabled:opacity-50"
            style={{ background: 'var(--accent)', color: '#0a0906' }}
            onMouseEnter={e => { (e.currentTarget as HTMLElement).style.filter = 'brightness(1.1)'; }}
            onMouseLeave={e => { (e.currentTarget as HTMLElement).style.filter = 'none'; }}
          >
            Re-analyze to generate
          </button>
        </div>
      ) : (
        <div className="space-y-3 animate-fade-up stagger-2">
          {visibleBlocks.map(block => (
            <ContentCard key={block.id} block={block} repoId={repoId!} onSaved={handleSaved} />
          ))}
        </div>
      )}

      {/* Re-analyze overlay */}
      {isAnalyzing && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center animate-fade-in"
          style={{ background: 'rgba(10,9,6,0.7)', backdropFilter: 'blur(4px)' }}
        >
          <div
            className="p-8 rounded-2xl text-center"
            style={{ background: 'var(--bg-card)', border: '1px solid var(--border)' }}
          >
            <div
              className="w-8 h-8 border-2 border-t-transparent rounded-full animate-spin mx-auto mb-4"
              style={{ borderColor: 'var(--accent)', borderTopColor: 'transparent' }}
            />
            <p className="font-medium mb-1" style={{ color: 'var(--text-primary)' }}>Analyzing repository…</p>
            <p className="text-sm" style={{ color: 'var(--text-muted)' }}>Extracting signals and generating content</p>
          </div>
        </div>
      )}
    </Layout>
  );
}
