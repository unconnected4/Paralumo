import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { SenseCard } from './SenseCard';
import type { Sense } from '../api/types';

describe('SenseCard', () => {
  const sampleSense: Sense = {
    id: 'sense_test_1',
    target_lang: 'ru',
    translation: 'бежать',
    synonyms: ['мчаться', 'нестись'],
    meanings: ['move fast using legs'],
    examples: [{ text: 'She runs every morning.', translation: 'Она бегает каждое утро.' }],
  };

  it('renders translation, synonyms, meanings, and examples', () => {
    render(<SenseCard sense={sampleSense} onSave={vi.fn()} onUnsave={vi.fn()} />);

    expect(screen.getByText('бежать')).toBeInTheDocument();
    expect(screen.getByText('(мчаться, нестись)')).toBeInTheDocument();
    expect(screen.getByText('move fast using legs')).toBeInTheDocument();
    expect(screen.getByText('She runs every morning.')).toBeInTheDocument();
    expect(screen.getByText('Она бегает каждое утро.')).toBeInTheDocument();
  });

  it('renders Save button when not saved and triggers onSave callback', () => {
    const handleSave = vi.fn();
    render(<SenseCard sense={sampleSense} onSave={handleSave} onUnsave={vi.fn()} />);

    const saveBtn = screen.getByRole('button', { name: 'Save' });
    expect(saveBtn).toBeInTheDocument();

    fireEvent.click(saveBtn);
    expect(handleSave).toHaveBeenCalledWith('sense_test_1');
  });

  it('renders a Saved button when the sense is saved and unsaves its card on click', () => {
    const handleSave = vi.fn();
    const handleUnsave = vi.fn();
    render(
      <SenseCard sense={sampleSense} cardId="card_test_1" onSave={handleSave} onUnsave={handleUnsave} />
    );

    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Saved' }));
    expect(handleUnsave).toHaveBeenCalledWith('card_test_1');
    expect(handleSave).not.toHaveBeenCalled();
  });

  it('disables the button while the card is being removed', () => {
    render(
      <SenseCard sense={sampleSense} cardId="card_test_1" onSave={vi.fn()} onUnsave={vi.fn()} isRemoving />
    );

    expect(screen.getByRole('button', { name: 'Removing...' })).toBeDisabled();
  });

  it('shows an inactive button, neither Save nor Saved, while the saved state is being checked', () => {
    const handleSave = vi.fn();
    const handleUnsave = vi.fn();
    render(
      <SenseCard
        sense={sampleSense}
        cardId="card_test_1"
        isCheckingSaved
        onSave={handleSave}
        onUnsave={handleUnsave}
      />
    );

    const button = screen.getByRole('button', { name: 'Checking saved state' });
    expect(button).toBeDisabled();
    expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Saved' })).not.toBeInTheDocument();

    fireEvent.click(button);
    expect(handleSave).not.toHaveBeenCalled();
    expect(handleUnsave).not.toHaveBeenCalled();
  });
});
