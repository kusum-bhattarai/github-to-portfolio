import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api, BACKEND_URL } from '../lib/api';
import type { AnalysisJob, ContentBlock, ContentType } from '../lib/api';
import { useCurrentUser } from '../hooks/useCurrentUser';

// ── Copy button ───────────────────────────────────────────────────────────────

function CopyButton({ text }: { text: string }) {
  const [copied, setCopied] = useState(false);
  const copy = () => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };
  return (
    <button onClick={copy} className="text-xs text-gray-500 hover:text-white transition-colors">
      {copied ? 'Copied!' : 'Copy'}
    </button>
  );
}

// ── Content card with inline editing ─────────────────────────────────────────

function ContentCard({
  block,
  repoId,
  onSaved,
}: {
  block: ContentBlock;
  repoId: string;
  onSaved: (contentId: string, newText: string) => void;
}) {
  const displayText = block.editedText ?? block.generatedText;
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState(displayText);

  const saveMutation = useMutation({
    mutationFn: (text: string) => api.projects.saveEdit(repoId, block.id, text),
    onSuccess: () => {
      onSaved(block.id, draft);
      setEditing(false);
    },
  });

  const handleEdit = () => {
    setDraft(displayText);
    setEditing(true);
  };

  const handleCancel = () => {
    setDraft(displayText);
    setEditing(false);
  };

  const labels: Record<ContentType, string> = {
    PORTFOLIO_SUMMARY: 'Portfolio Summary',
    RESUME_BULLETS: 'Resume Bullets',
    TECH_STACK: 'Tech Stack',
    PROJECT_TAGS: 'Project Tags',
    ONE_SENTENCE_PITCH: 'One-Sentence Pitch',
    TALKING_POINTS: 'Talking Points',
    INTERVIEW_STORY: 'Interview Story',
  };

  const editRows: Partial<Record<ContentType, number>> = {
    PORTFOLIO_SUMMARY: 4,
    INTERVIEW_STORY: 14,
    TALKING_POINTS: 10,
    RESUME_BULLETS: 8,
  };

  const renderContent = () => {
    if (block.contentType === 'RESUME_BULLETS' || block.contentType === 'TALKING_POINTS') {
      const bullets = displayText.split('\n').filter(Boolean);
      return (
        <ul className="space-y-2">
          {bullets.map((b, i) => (
            <li key={i} className="flex gap-2 text-gray-300 text-sm">
              <span className="text-violet-400 mt-0.5 shrink-0">•</span>
              <span>{b.replace(/^[-•]\s*/, '')}</span>
            </li>
          ))}
        </ul>
      );
    }
    if (block.contentType === 'TECH_STACK' || block.contentType === 'PROJECT_TAGS') {
      const items = displayText.split(',').map(s => s.trim()).filter(Boolean);
      return (
        <div className="flex flex-wrap gap-2">
          {items.map(item => (
            <span key={item} className="px-2.5 py-1 bg-gray-800 border border-gray-700 rounded-lg text-sm text-gray-300">
              {item}
            </span>
          ))}
        </div>
      );
    }
    if (block.contentType === 'ONE_SENTENCE_PITCH') {
      return (
        <p className="text-gray-100 text-base font-medium leading-relaxed italic">
          "{displayText}"
        </p>
      );
    }
    if (block.contentType === 'INTERVIEW_STORY') {
      // Render each labeled section as its own block
      const sections = displayText.split(/\n\n+/).filter(Boolean);
      return (
        <div className="space-y-4">
          {sections.map((section, i) => {
            const colonIdx = section.indexOf(':');
            if (colonIdx !== -1) {
              const label = section.slice(0, colonIdx).trim();
              const body = section.slice(colonIdx + 1).trim();
              return (
                <div key={i}>
                  <p className="text-xs font-semibold text-violet-400 uppercase tracking-wider mb-1">{label}</p>
                  <p className="text-gray-300 text-sm leading-relaxed">{body}</p>
                </div>
              );
            }
            return <p key={i} className="text-gray-300 text-sm leading-relaxed">{section}</p>;
          })}
        </div>
      );
    }
    return <p className="text-gray-300 text-sm leading-relaxed">{displayText}</p>;
  };

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
      {/* Header */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <h3 className="text-sm font-semibold text-white uppercase tracking-wider">
            {labels[block.contentType] ?? block.contentType}
          </h3>
          {block.isEdited && (
            <span className="px-1.5 py-0.5 text-[10px] font-medium bg-violet-950 text-violet-400 border border-violet-800/50 rounded">
              Edited
            </span>
          )}
        </div>
        <div className="flex items-center gap-3">
          {!editing && (
            <>
              <CopyButton text={displayText} />
              <button
                onClick={handleEdit}
                className="text-xs text-gray-500 hover:text-white transition-colors"
              >
                Edit
              </button>
            </>
          )}
        </div>
      </div>

      {/* Editing mode */}
      {editing ? (
        <div className="space-y-3">
          <textarea
            value={draft}
            onChange={e => setDraft(e.target.value)}
            rows={editRows[block.contentType] ?? 6}
            className="w-full bg-gray-800 border border-gray-700 focus:border-violet-500 rounded-lg px-3 py-2.5 text-sm text-gray-200 placeholder-gray-600 resize-y outline-none transition-colors"
            autoFocus
          />
          <div className="flex items-center gap-2">
            <button
              onClick={() => saveMutation.mutate(draft)}
              disabled={saveMutation.isPending || draft.trim() === displayText.trim()}
              className="px-3 py-1.5 bg-violet-600 hover:bg-violet-500 disabled:opacity-40 rounded-lg text-xs font-medium text-white transition-colors"
            >
              {saveMutation.isPending ? 'Saving…' : 'Save'}
            </button>
            <button
              onClick={handleCancel}
              disabled={saveMutation.isPending}
              className="px-3 py-1.5 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg text-xs font-medium text-gray-300 transition-colors"
            >
              Cancel
            </button>
            {block.isEdited && (
              <button
                onClick={() => {
                  setDraft(block.generatedText);
                  saveMutation.mutate(block.generatedText);
                }}
                disabled={saveMutation.isPending}
                className="ml-auto text-xs text-gray-600 hover:text-gray-400 transition-colors"
              >
                Reset to original
              </button>
            )}
          </div>
          {saveMutation.isError && (
            <p className="text-xs text-red-400">Failed to save. Please try again.</p>
          )}
        </div>
      ) : (
        renderContent()
      )}
    </div>
  );
}

// ── View toggle ───────────────────────────────────────────────────────────────

type View = 'resume' | 'interview';

const RESUME_TYPES: ContentType[] = ['PORTFOLIO_SUMMARY', 'RESUME_BULLETS', 'TECH_STACK', 'PROJECT_TAGS'];
const INTERVIEW_TYPES: ContentType[] = ['ONE_SENTENCE_PITCH', 'TALKING_POINTS', 'INTERVIEW_STORY'];

const RESUME_ORDER = ['PORTFOLIO_SUMMARY', 'RESUME_BULLETS', 'TECH_STACK', 'PROJECT_TAGS'];
const INTERVIEW_ORDER = ['ONE_SENTENCE_PITCH', 'TALKING_POINTS', 'INTERVIEW_STORY'];

// ── Page ─────────────────────────────────────────────────────────────────────

export default function ResultsPage() {
  const { repoId } = useParams<{ repoId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: user } = useCurrentUser();
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
      old?.map(b =>
        b.id === contentId ? { ...b, editedText: newText, isEdited: true } : b
      )
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

  return (
    <div className="min-h-screen bg-gray-950 text-white">
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

      <main className="max-w-3xl mx-auto px-6 py-8">
        {/* Title row */}
        <div className="flex items-center justify-between mb-6">
          <div className="flex items-center gap-3">
            <button
              onClick={() => navigate('/workspace')}
              className="text-gray-400 hover:text-white transition-colors text-sm flex items-center gap-1"
            >
              <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M15 19l-7-7 7-7" />
              </svg>
              Workspace
            </button>
            <span className="text-gray-700">/</span>
            <h2 className="text-2xl font-bold">Generated Content</h2>
            {editedCount > 0 && (
              <span className="px-2 py-0.5 text-xs bg-violet-950 text-violet-400 border border-violet-800/50 rounded-full">
                {editedCount} edited
              </span>
            )}
          </div>
          <button
            onClick={() => reanalyzeMutation.mutate()}
            disabled={reanalyzeMutation.isPending || !!activeJobId}
            className="flex items-center gap-2 px-4 py-2 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
          >
            <svg
              className={`w-4 h-4 ${reanalyzeMutation.isPending ? 'animate-spin' : ''}`}
              fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}
            >
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {(reanalyzeMutation.isPending || !!activeJobId) ? 'Analyzing...' : 'Re-analyze'}
          </button>
        </div>

        {/* View toggle */}
        {content.length > 0 && (
          <div className="flex gap-1 p-1 bg-gray-900 border border-gray-800 rounded-lg w-fit mb-6">
            <button
              onClick={() => setView('resume')}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
                view === 'resume'
                  ? 'bg-violet-600 text-white'
                  : 'text-gray-400 hover:text-white'
              }`}
            >
              Resume
            </button>
            <button
              onClick={() => setView('interview')}
              className={`px-4 py-1.5 rounded-md text-sm font-medium transition-colors ${
                view === 'interview'
                  ? 'bg-violet-600 text-white'
                  : 'text-gray-400 hover:text-white'
              }`}
            >
              Interview
              {!hasInterviewContent && (
                <span className="ml-1.5 text-[10px] text-gray-500">(re-analyze to generate)</span>
              )}
            </button>
          </div>
        )}

        {isLoading ? (
          <div className="text-center py-20 text-gray-500">Loading...</div>
        ) : content.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-gray-400 mb-4">No content generated yet.</p>
            <button
              onClick={() => reanalyzeMutation.mutate()}
              disabled={reanalyzeMutation.isPending || !!activeJobId}
              className="px-6 py-3 bg-violet-600 hover:bg-violet-500 rounded-xl font-semibold transition-colors disabled:opacity-50"
            >
              {reanalyzeMutation.isPending ? 'Analyzing...' : 'Analyze Now'}
            </button>
          </div>
        ) : visibleBlocks.length === 0 ? (
          <div className="text-center py-16 text-gray-500">
            <p className="mb-3">No interview content yet.</p>
            <button
              onClick={() => reanalyzeMutation.mutate()}
              disabled={reanalyzeMutation.isPending || !!activeJobId}
              className="px-5 py-2 bg-violet-600 hover:bg-violet-500 rounded-lg text-sm font-semibold text-white transition-colors disabled:opacity-50"
            >
              Re-analyze to generate
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {visibleBlocks.map(block => (
              <ContentCard
                key={block.id}
                block={block}
                repoId={repoId!}
                onSaved={handleSaved}
              />
            ))}
          </div>
        )}

        {(reanalyzeMutation.isPending || !!activeJobId) && (
          <div className="fixed inset-0 bg-black/50 flex items-center justify-center z-50">
            <div className="bg-gray-900 border border-gray-800 rounded-2xl p-8 text-center">
              <div className="w-8 h-8 border-2 border-violet-500 border-t-transparent rounded-full animate-spin mx-auto mb-4" />
              <p className="text-white font-medium">Analyzing repository...</p>
              <p className="text-gray-400 text-sm mt-1">Extracting signals and generating content</p>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
