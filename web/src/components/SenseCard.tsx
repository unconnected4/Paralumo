import { Check, Plus, Loader2 } from 'lucide-react';
import type { Sense } from '../api/types';

interface SenseCardProps {
  sense: Sense;
  onSave: (senseId: string) => void;
  onUnsave: (cardId: string) => void;
  isSaving?: boolean;
  isRemoving?: boolean;
}

export function SenseCard({
  sense,
  onSave,
  onUnsave,
  isSaving = false,
  isRemoving = false,
}: SenseCardProps) {
  // The API sends `card_id` exactly when the sense is saved; it is what unsaving needs.
  const cardId = sense.card_id;
  const isSaved = cardId !== undefined;

  return (
    <div
      className={`border rounded-xl p-4 transition-all duration-200 bg-white ${
        isSaved
          ? 'border-emerald-200 bg-emerald-50/20 shadow-xs'
          : 'border-slate-200 hover:border-indigo-200 hover:shadow-md'
      }`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          {/* Main Russian translation */}
          <div className="flex items-baseline gap-2 flex-wrap">
            <h4 className="text-lg font-semibold text-slate-900">{sense.translation_ru}</h4>
            {sense.synonyms_ru && sense.synonyms_ru.length > 0 && (
              <span className="text-sm text-slate-500">
                ({sense.synonyms_ru.join(', ')})
              </span>
            )}
          </div>

          {/* English definition / meaning */}
          {sense.meanings_en && sense.meanings_en.length > 0 && (
            <p className="mt-1 text-sm text-slate-600 italic">
              {sense.meanings_en.join('; ')}
            </p>
          )}

          {/* Example sentences */}
          {sense.examples && sense.examples.length > 0 && (
            <div className="mt-3 space-y-1.5 pl-3 border-l-2 border-indigo-200">
              {sense.examples.map((ex, idx) => (
                <div key={idx} className="text-sm">
                  <p className="text-slate-800 font-medium">{ex.en}</p>
                  <p className="text-slate-500 text-xs mt-0.5">{ex.ru}</p>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* Action button: '+' saves, '✓' unsaves */}
        <div>
          {isSaved ? (
            <button
              type="button"
              onClick={() => onUnsave(cardId)}
              disabled={isRemoving}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-emerald-100 text-emerald-800 border border-emerald-300 hover:bg-emerald-200 active:bg-emerald-300 transition-colors disabled:opacity-60 focus:outline-hidden focus:ring-2 focus:ring-emerald-500 focus:ring-offset-1"
              title="Remove from My Words"
            >
              {isRemoving ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Removing...
                </>
              ) : (
                <>
                  <Check className="w-4 h-4 stroke-[2.5]" />
                  Saved
                </>
              )}
            </button>
          ) : (
            <button
              type="button"
              onClick={() => onSave(sense.id)}
              disabled={isSaving}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-semibold bg-indigo-600 text-white hover:bg-indigo-700 active:bg-indigo-800 transition-colors shadow-xs disabled:opacity-60 focus:outline-hidden focus:ring-2 focus:ring-indigo-500 focus:ring-offset-1"
              title="Save this sense to My Words"
            >
              {isSaving ? (
                <>
                  <Loader2 className="w-4 h-4 animate-spin" />
                  Saving...
                </>
              ) : (
                <>
                  <Plus className="w-4 h-4 stroke-[2.5]" />
                  Save
                </>
              )}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}
