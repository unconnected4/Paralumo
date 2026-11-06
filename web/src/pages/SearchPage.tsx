import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router';
import { Search as SearchIcon, X, AlertCircle, Sparkles, Loader2 } from 'lucide-react';
import { useLookup } from '../hooks/useDictionary';
import { useCards } from '../hooks/useCards';
import { useAuth } from '../hooks/useAuth';
import { SenseCard } from '../components/SenseCard';
import { AudioButton } from '../components/AudioButton';

const SAMPLE_WORDS = ['run', 'bank', 'light'];

interface SearchFormProps {
  initialTerm: string;
  onSearch: (term: string) => void;
  onClear: () => void;
}

// Owns the text being typed. It is remounted (keyed) whenever the active query changes,
// so the input follows the URL on back/forward and links without an effect.
function SearchForm({ initialTerm, onSearch, onClear }: SearchFormProps) {
  const [searchTerm, setSearchTerm] = useState(initialTerm);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = searchTerm.trim();
    if (trimmed) onSearch(trimmed);
  };

  const handleClear = () => {
    setSearchTerm('');
    onClear();
  };

  return (
    <form onSubmit={handleSubmit} className="relative flex items-center">
      <div className="absolute left-4 text-slate-400 pointer-events-none">
        <SearchIcon className="w-5 h-5" />
      </div>
      <input
        type="text"
        value={searchTerm}
        onChange={(e) => setSearchTerm(e.target.value)}
        placeholder="Search an English word (e.g. run, bank, light)..."
        className="w-full pl-12 pr-24 py-3.5 bg-white border border-slate-300 rounded-2xl shadow-xs text-slate-900 placeholder-slate-400 focus:outline-hidden focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 text-base sm:text-lg transition-all"
        autoFocus
      />
      {searchTerm && (
        <button
          type="button"
          onClick={handleClear}
          className="absolute right-20 text-slate-400 hover:text-slate-600 p-1.5 rounded-full hover:bg-slate-100 transition-colors"
          title="Clear search"
        >
          <X className="w-4 h-4" />
        </button>
      )}
      <button
        type="submit"
        className="absolute right-2.5 px-4 py-2 bg-indigo-600 hover:bg-indigo-700 active:bg-indigo-800 text-white font-medium rounded-xl text-sm transition-colors shadow-xs focus:outline-hidden focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1"
      >
        Search
      </button>
    </form>
  );
}

export function SearchPage() {
  // The URL (`/?q=word`) is the single source of truth for the active search, so a shared link,
  // a reload and the browser's back/forward buttons all show the same word.
  const [searchParams, setSearchParams] = useSearchParams();
  const activeQuery = searchParams.get('q')?.trim() ?? '';

  const { data, isLoading, isError, error } = useLookup(activeQuery);
  const { saveCard, isSaving, savingSenseId, deleteCard, isDeleting, deletingCardId } = useCards();
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const search = (term: string) => setSearchParams({ q: term });
  const clear = () => setSearchParams({});

  // Saving needs an account; send signed-out users to sign in instead of failing with 401,
  // and bring them back to this search afterwards.
  const handleSave = (senseId: string) => {
    if (!isAuthenticated) {
      navigate('/auth', { state: { from: `${location.pathname}${location.search}` } });
      return;
    }
    saveCard(senseId);
  };

  const isNotFound = error?.status === 404;

  const lexemes = data?.lexemes || [];
  const primaryTranscription = lexemes.find((l) => l.transcription)?.transcription;

  return (
    <div className="max-w-4xl mx-auto px-4 py-8 space-y-8">
      {/* Search Bar */}
      <section className="space-y-3">
        <SearchForm key={activeQuery} initialTerm={activeQuery} onSearch={search} onClear={clear} />

        {/* Sample query shortcuts */}
        <div className="flex items-center gap-2 text-xs text-slate-500 flex-wrap">
          <span className="flex items-center gap-1 font-medium text-slate-600">
            <Sparkles className="w-3.5 h-3.5 text-amber-500" />
            Try samples:
          </span>
          {SAMPLE_WORDS.map((w) => (
            <button
              key={w}
              type="button"
              onClick={() => search(w)}
              className={`px-2.5 py-1 rounded-full border transition-all ${
                activeQuery.toLowerCase() === w
                  ? 'bg-indigo-100 border-indigo-300 text-indigo-700 font-semibold shadow-xs'
                  : 'bg-white border-slate-200 text-slate-600 hover:border-indigo-300 hover:text-indigo-600'
              }`}
            >
              {w}
            </button>
          ))}
        </div>
      </section>

      {/* Loading state */}
      {isLoading && (
        <div className="py-16 text-center text-slate-500 flex flex-col items-center justify-center gap-3">
          <Loader2 className="w-8 h-8 animate-spin text-indigo-600" />
          <p className="text-sm font-medium">Looking up "{activeQuery}"...</p>
        </div>
      )}

      {/* Error state */}
      {isError && (
        <div className="p-6 bg-rose-50 border border-rose-200 rounded-2xl flex items-start gap-4 text-rose-800">
          <AlertCircle className="w-6 h-6 text-rose-600 shrink-0 mt-0.5" />
          <div>
            <h3 className="font-semibold text-rose-900">
              {isNotFound ? 'Word not found' : 'Lookup failed'}
            </h3>
            <p className="text-sm mt-1 text-rose-700">{error?.message}</p>
          </div>
        </div>
      )}

      {/* Empty initial state */}
      {!isLoading && !isError && activeQuery === '' && (
        <div className="text-center py-16 bg-white border border-dashed border-slate-200 rounded-2xl p-8">
          <SearchIcon className="w-12 h-12 text-slate-300 mx-auto mb-3" />
          <h3 className="text-lg font-semibold text-slate-700">Enter a word to explore senses</h3>
          <p className="text-sm text-slate-500 mt-1 max-w-md mx-auto">
            Search for English words to see Russian translations, context meanings, and example sentences. Save specific senses to build your personal vocabulary.
          </p>
        </div>
      )}

      {/* Dictionary Results */}
      {!isLoading && !isError && lexemes.length > 0 && (
        <div className="space-y-6">
          {/* Lemma Header */}
          <div className="flex items-center justify-between pb-4 border-b border-slate-200">
            <div className="flex items-baseline gap-3 flex-wrap">
              <h2 className="text-3xl font-extrabold text-slate-900 capitalize tracking-tight">
                {activeQuery}
              </h2>
              {primaryTranscription && (
                <span className="text-base text-slate-500 font-mono">
                  [{primaryTranscription}]
                </span>
              )}
              <AudioButton text={activeQuery} />
            </div>
            <span className="text-xs text-slate-400">
              {lexemes.reduce((acc, l) => acc + l.senses.length, 0)} senses available
            </span>
          </div>

          {/* Senses grouped by Part of Speech */}
          <div className="space-y-8">
            {lexemes.map((lexeme) => (
              <div key={`${lexeme.lemma}-${lexeme.pos}`} className="space-y-3">
                <div className="flex items-center gap-2">
                  <span className="px-2.5 py-0.5 rounded-md text-xs font-bold uppercase tracking-wider bg-indigo-100 text-indigo-800">
                    {lexeme.pos}
                  </span>
                  {lexeme.transcription && lexeme.transcription !== primaryTranscription && (
                    <span className="text-xs text-slate-400 font-mono">[{lexeme.transcription}]</span>
                  )}
                </div>

                <div className="grid gap-3">
                  {lexeme.senses.map((sense) => (
                    <SenseCard
                      key={sense.id}
                      sense={sense}
                      onSave={handleSave}
                      onUnsave={deleteCard}
                      isSaving={isSaving && savingSenseId === sense.id}
                      isRemoving={isDeleting && deletingCardId === sense.card_id}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
