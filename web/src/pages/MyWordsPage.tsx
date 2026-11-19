import { useState } from 'react';
import { Link, Navigate } from 'react-router';
import { Bookmark, Trash2, Search, Plus, Sparkles, Loader2 } from 'lucide-react';
import { useCards } from '../hooks/useCards';
import { useAuth } from '../hooks/useAuth';
import { AudioButton } from '../components/AudioButton';

export function MyWordsPage() {
  const { cards, isLoading: isCardsLoading, deleteCard, isDeleting, deletingCardId } = useCards();
  const { isAuthenticated, isLoading: isAuthLoading } = useAuth();
  const isLoading = isCardsLoading || isAuthLoading;
  const [filterQuery, setFilterQuery] = useState('');

  // "My words" is per-user: signed-out visitors go to sign in rather than seeing an empty deck.
  if (!isAuthLoading && !isAuthenticated) {
    return <Navigate to="/auth" replace state={{ from: '/my-words' }} />;
  }

  const filteredCards = cards.filter((card) => {
    if (!filterQuery.trim()) return true;
    const q = filterQuery.toLowerCase();
    return (
      card.lemma.toLowerCase().includes(q) ||
      card.translation.toLowerCase().includes(q) ||
      (card.synonyms && card.synonyms.some((s) => s.toLowerCase().includes(q))) ||
      (card.meanings && card.meanings.some((m) => m.toLowerCase().includes(q)))
    );
  });

  return (
    <div className="max-w-4xl mx-auto px-4 py-8 space-y-8">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-6 border-b border-slate-200">
        <div>
          <div className="flex items-center gap-2">
            <h1 className="text-3xl font-extrabold text-slate-900 tracking-tight">My Words</h1>
            <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-indigo-100 text-indigo-700">
              {cards.length} {cards.length === 1 ? 'card' : 'cards'}
            </span>
          </div>
          <p className="text-sm text-slate-500 mt-1">
            Personal vocabulary deck saved with per-sense precision.
          </p>
        </div>

        <Link
          to="/"
          className="inline-flex items-center justify-center gap-1.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-semibold shadow-xs transition-colors"
        >
          <Plus className="w-4 h-4" />
          <span>Find more words</span>
        </Link>
      </div>

      {/* Filter / Search within saved cards */}
      {cards.length > 0 && (
        <div className="relative">
          <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" />
          <input
            type="text"
            value={filterQuery}
            onChange={(e) => setFilterQuery(e.target.value)}
            placeholder="Filter saved words by English or Russian..."
            className="w-full pl-10 pr-4 py-2.5 bg-white border border-slate-200 rounded-xl text-sm focus:outline-hidden focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 shadow-xs"
          />
        </div>
      )}

      {/* Loading state */}
      {isLoading && (
        <div className="py-16 text-center text-slate-500 flex flex-col items-center justify-center gap-3">
          <Loader2 className="w-8 h-8 animate-spin text-indigo-600" />
          <p className="text-sm">Loading vocabulary deck...</p>
        </div>
      )}

      {/* Empty State */}
      {!isLoading && cards.length === 0 && (
        <div className="text-center py-16 bg-white border border-dashed border-slate-200 rounded-2xl p-8 space-y-4">
          <div className="w-12 h-12 bg-indigo-50 rounded-2xl flex items-center justify-center mx-auto text-indigo-600">
            <Bookmark className="w-6 h-6" />
          </div>
          <div className="max-w-sm mx-auto">
            <h3 className="text-lg font-bold text-slate-800">Your deck is empty</h3>
            <p className="text-sm text-slate-500 mt-1">
              Search for words in the dictionary and click the <strong>+ Save</strong> button on any sense to add it here.
            </p>
          </div>
          <Link
            to="/?q=run"
            className="inline-flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl text-sm font-semibold shadow-xs transition-colors"
          >
            <Sparkles className="w-4 h-4" />
            <span>Try searching "run"</span>
          </Link>
        </div>
      )}

      {/* Cards list */}
      {!isLoading && cards.length > 0 && (
        <div className="space-y-4">
          {filteredCards.length === 0 ? (
            <div className="text-center py-8 text-slate-500 text-sm">
              No saved words match "{filterQuery}".
            </div>
          ) : (
            filteredCards.map((card) => (
              <div
                key={card.id}
                className="bg-white border border-slate-200 rounded-xl p-5 shadow-xs hover:border-slate-300 transition-all flex flex-col sm:flex-row sm:items-start justify-between gap-4"
              >
                <div className="flex-1 min-w-0 space-y-2">
                  {/* Lemma & POS */}
                  <div className="flex items-baseline gap-2.5 flex-wrap">
                    <span className="text-xl font-bold text-slate-900">{card.lemma}</span>
                    <span className="px-2 py-0.5 rounded-sm text-[11px] font-bold uppercase tracking-wider bg-slate-100 text-slate-700">
                      {card.pos}
                    </span>
                    {card.transcription && (
                      <span className="text-sm text-slate-400 font-mono">[{card.transcription}]</span>
                    )}
                    <AudioButton text={card.lemma} className="ml-1" />
                  </div>

                  {/* Translation & synonyms */}
                  <div className="flex items-baseline gap-2 flex-wrap">
                    <span className="text-base font-semibold text-emerald-800">
                      {card.translation}
                    </span>
                    {card.synonyms && card.synonyms.length > 0 && (
                      <span className="text-xs text-slate-500">
                        ({card.synonyms.join(', ')})
                      </span>
                    )}
                  </div>

                  {/* Meanings */}
                  {card.meanings && card.meanings.length > 0 && (
                    <p className="text-xs text-slate-600 italic">
                      {card.meanings.join('; ')}
                    </p>
                  )}

                  {/* Examples */}
                  {card.examples && card.examples.length > 0 && (
                    <div className="mt-2 text-xs space-y-1 pl-3 border-l-2 border-indigo-200">
                      {card.examples.map((ex, idx) => (
                        <div key={idx}>
                          <p className="text-slate-800 font-medium">{ex.text}</p>
                          <p className="text-slate-500">{ex.translation}</p>
                        </div>
                      ))}
                    </div>
                  )}

                  {/* Notes if present */}
                  {card.notes && (
                    <p className="text-xs text-amber-700 bg-amber-50 rounded-sm px-2 py-1 mt-1 border border-amber-200">
                      Note: {card.notes}
                    </p>
                  )}
                </div>

                {/* Delete button */}
                <div className="self-end sm:self-start shrink-0">
                  <button
                    type="button"
                    onClick={() => deleteCard(card.id)}
                    disabled={isDeleting && deletingCardId === card.id}
                    title="Remove from My Words"
                    className="p-2 text-slate-400 hover:text-rose-600 hover:bg-rose-50 rounded-lg transition-colors focus:outline-hidden focus:ring-2 focus:ring-rose-500"
                  >
                    {isDeleting && deletingCardId === card.id ? (
                      <Loader2 className="w-4 h-4 animate-spin" />
                    ) : (
                      <Trash2 className="w-4 h-4" />
                    )}
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
