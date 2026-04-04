import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../lib/api';
import type { ContentBlock } from '../lib/api';
import { useCurrentUser } from '../hooks/useCurrentUser';

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

function ContentCard({ block }: { block: ContentBlock }) {
  const labels: Record<string, string> = {
    PORTFOLIO_SUMMARY: 'Portfolio Summary',
    RESUME_BULLETS: 'Resume Bullets',
    TECH_STACK: 'Tech Stack',
    PROJECT_TAGS: 'Project Tags',
  };

  const renderContent = () => {
    if (block.contentType === 'RESUME_BULLETS') {
      const bullets = block.generatedText.split('\n').filter(Boolean);
      return (
        <ul className="space-y-2">
          {bullets.map((b, i) => (
            <li key={i} className="flex gap-2 text-gray-300 text-sm">
              <span className="text-violet-400 mt-0.5">•</span>
              <span>{b.replace(/^[-•]\s*/, '')}</span>
            </li>
          ))}
        </ul>
      );
    }

    if (block.contentType === 'TECH_STACK' || block.contentType === 'PROJECT_TAGS') {
      const items = block.generatedText.split(',').map(s => s.trim()).filter(Boolean);
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

    return <p className="text-gray-300 text-sm leading-relaxed">{block.generatedText}</p>;
  };

  return (
    <div className="bg-gray-900 border border-gray-800 rounded-xl p-5">
      <div className="flex items-center justify-between mb-3">
        <h3 className="text-sm font-semibold text-white uppercase tracking-wider">
          {labels[block.contentType] ?? block.contentType}
        </h3>
        <CopyButton text={block.generatedText} />
      </div>
      {renderContent()}
    </div>
  );
}

export default function ResultsPage() {
  const { repoId } = useParams<{ repoId: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { data: user } = useCurrentUser();

  const { data: content = [], isLoading } = useQuery({
    queryKey: ['content', repoId],
    queryFn: () => api.analysis.getContent(repoId!),
    enabled: !!repoId,
  });

  const reanalyzeMutation = useMutation({
    mutationFn: () => api.analysis.analyze(repoId!),
    onSuccess: (data) => {
      queryClient.setQueryData(['content', repoId], data);
    },
  });

  const handleLogout = () => { window.location.href = '/auth/logout'; };

  const ORDER = ['PORTFOLIO_SUMMARY', 'RESUME_BULLETS', 'TECH_STACK', 'PROJECT_TAGS'];
  const sorted = [...content].sort((a, b) => ORDER.indexOf(a.contentType) - ORDER.indexOf(b.contentType));

  return (
    <div className="min-h-screen bg-gray-950 text-white">
      <header className="border-b border-gray-800 px-6 py-4 flex items-center justify-between">
        <h1 className="text-lg font-semibold">GitHub Portfolio Intelligence</h1>
        <div className="flex items-center gap-4">
          {user?.avatarUrl && <img src={user.avatarUrl} alt={user.username} className="w-8 h-8 rounded-full" />}
          <span className="text-gray-300 text-sm">{user?.username}</span>
          <button onClick={handleLogout} className="text-sm text-gray-400 hover:text-white transition-colors">Log out</button>
        </div>
      </header>

      <main className="max-w-3xl mx-auto px-6 py-8">
        <div className="flex items-center justify-between mb-8">
          <div className="flex items-center gap-3">
            <button onClick={() => navigate('/dashboard')} className="text-gray-400 hover:text-white transition-colors">
              ← Back
            </button>
            <h2 className="text-2xl font-bold">Generated Content</h2>
          </div>
          <button
            onClick={() => reanalyzeMutation.mutate()}
            disabled={reanalyzeMutation.isPending}
            className="flex items-center gap-2 px-4 py-2 bg-gray-800 hover:bg-gray-700 border border-gray-700 rounded-lg text-sm font-medium transition-colors disabled:opacity-50"
          >
            <svg className={`w-4 h-4 ${reanalyzeMutation.isPending ? 'animate-spin' : ''}`} fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
              <path strokeLinecap="round" strokeLinejoin="round" d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            {reanalyzeMutation.isPending ? 'Analyzing...' : 'Re-analyze'}
          </button>
        </div>

        {isLoading ? (
          <div className="text-center py-20 text-gray-500">Loading...</div>
        ) : content.length === 0 ? (
          <div className="text-center py-20">
            <p className="text-gray-400 mb-4">No content generated yet.</p>
            <button
              onClick={() => reanalyzeMutation.mutate()}
              disabled={reanalyzeMutation.isPending}
              className="px-6 py-3 bg-violet-600 hover:bg-violet-500 rounded-xl font-semibold transition-colors disabled:opacity-50"
            >
              {reanalyzeMutation.isPending ? 'Analyzing...' : 'Analyze Now'}
            </button>
          </div>
        ) : (
          <div className="space-y-4">
            {sorted.map(block => <ContentCard key={block.id} block={block} />)}
          </div>
        )}

        {reanalyzeMutation.isPending && (
          <div className="fixed inset-0 bg-black/50 flex items-center justify-center">
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
